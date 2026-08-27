#!/usr/bin/env python3
"""Generate the release-owned BankBook vector asset.

The Android runtime uses the same WordPiece rules, MiniLM TFLite artifact,
sequence length, chunk size, and overlap. The script fails when the model or
corpus is missing; it never falls back to hashes, random vectors, or lexical
features. Run it from the repository root after verifying the model license
and checksum.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import re
import struct
import tempfile
from pathlib import Path

import numpy as np
import tensorflow as tf


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--corpus",
        type=Path,
        default=Path("app/src/main/assets/bankbook_rag_chunks_medgemma.jsonl"),
    )
    parser.add_argument(
        "--model",
        type=Path,
        default=Path("app/src/main/assets/embedding/all-MiniLM-L6-v2.tflite"),
    )
    parser.add_argument(
        "--vocab",
        type=Path,
        default=Path("app/src/main/assets/embedding/vocab.txt"),
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("app/src/main/assets/embedding/bankbook_embeddings.f32"),
    )
    parser.add_argument("--chunk-size", type=int, default=120)
    parser.add_argument("--overlap", type=int, default=20)
    parser.add_argument("--max-length", type=int, default=128)
    parser.add_argument("--dimensions", type=int, default=384)
    return parser.parse_args()


def load_vocab(path: Path) -> dict[str, int]:
    with path.open(encoding="utf-8") as handle:
        vocab = {line.strip(): index for index, line in enumerate(handle) if line.strip()}
    required = {"[CLS]", "[SEP]", "[UNK]", "[PAD]"}
    missing = required.difference(vocab)
    if missing:
        raise RuntimeError(f"Vocabulary is missing required tokens: {sorted(missing)}")
    return vocab


def word_pieces(word: str, vocab: dict[str, int]) -> list[str]:
    pieces: list[str] = []
    start = 0
    while start < len(word):
        matched: str | None = None
        for end in range(len(word), start, -1):
            candidate = word[start:end] if start == 0 else f"##{word[start:end]}"
            if candidate in vocab:
                matched = candidate
                break
        if matched is None:
            return ["[UNK]"]
        pieces.append(matched)
        start = end
    return pieces


def encode(text: str, vocab: dict[str, int], max_length: int) -> tuple[np.ndarray, np.ndarray]:
    tokens = ["[CLS]"]
    for word in re.split(r"[\s\W_]+", text.lower(), flags=re.UNICODE):
        if word:
            tokens.extend(word_pieces(word, vocab))
    tokens.append("[SEP]")
    tokens = tokens[:max_length]
    token_ids = [vocab.get(token, vocab["[UNK]"]) for token in tokens]
    pad_id = vocab["[PAD]"]
    ids = token_ids + [pad_id] * (max_length - len(token_ids))
    mask = [1] * len(token_ids) + [0] * (max_length - len(token_ids))
    return (
        np.asarray([ids], dtype=np.int32),
        np.asarray([mask], dtype=np.int32),
    )


def load_chunks(path: Path, chunk_size: int, overlap: int) -> list[str]:
    if chunk_size <= overlap or overlap < 0:
        raise ValueError("chunk-size must be greater than overlap, and overlap cannot be negative")
    chunks: list[str] = []
    with path.open(encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            record = json.loads(line)
            text = str(record.get("text", "")).strip()
            if not text:
                continue
            words = re.split(r"\s+", text)
            words = [word for word in words if word]
            for start in range(0, len(words), chunk_size - overlap):
                end = min(start + chunk_size, len(words))
                chunks.append(" ".join(words[start:end]))
                if end == len(words):
                    break
    if not chunks:
        raise RuntimeError(f"No text chunks found in {path}")
    return chunks


def main() -> None:
    args = parse_args()
    for path in (args.corpus, args.model, args.vocab):
        if not path.is_file():
            raise FileNotFoundError(path)

    vocab = load_vocab(args.vocab)
    chunks = load_chunks(args.corpus, args.chunk_size, args.overlap)
    interpreter = tf.lite.Interpreter(model_path=str(args.model))
    input_details = interpreter.get_input_details()
    if len(input_details) < 2:
        raise RuntimeError("Embedding model must expose input IDs and attention mask")
    interpreter.resize_tensor_input(input_details[0]["index"], [1, args.max_length])
    interpreter.resize_tensor_input(input_details[1]["index"], [1, args.max_length])
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    if len(output_details) < 1:
        raise RuntimeError("Embedding model has no output tensor")
    output_index = output_details[0]["index"]

    args.output.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary_name = tempfile.mkstemp(prefix="bankbook-embeddings-", suffix=".f32", dir=args.output.parent)
    os.close(fd)
    digest = hashlib.sha256()
    try:
        with open(temporary_name, "wb") as output:
            for index, text in enumerate(chunks, start=1):
                input_ids, attention_mask = encode(text, vocab, args.max_length)
                # Keep this order identical to LocalEmbedder.kt: tensor 0 is
                # input IDs and tensor 1 is the attention mask.
                interpreter.set_tensor(input_details[0]["index"], input_ids)
                interpreter.set_tensor(input_details[1]["index"], attention_mask)
                interpreter.invoke()
                vector = np.asarray(interpreter.get_tensor(output_index), dtype=np.float32).reshape(-1)
                if vector.size != args.dimensions or not np.isfinite(vector).all():
                    raise RuntimeError(f"Invalid embedding output at chunk {index}")
                norm = float(np.linalg.norm(vector))
                if not math.isfinite(norm) or norm <= 1e-8:
                    raise RuntimeError(f"Zero embedding output at chunk {index}")
                vector = vector / norm
                encoded = vector.astype("<f4", copy=False).tobytes()
                output.write(encoded)
                digest.update(encoded)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary_name, args.output)
    except BaseException:
        if os.path.exists(temporary_name):
            os.unlink(temporary_name)
        raise

    byte_size = args.output.stat().st_size
    expected_size = len(chunks) * args.dimensions * struct.calcsize("<f")
    if byte_size != expected_size:
        raise RuntimeError(f"Generated byte size {byte_size} does not equal expected size {expected_size}")
    print(
        json.dumps(
            {
                "chunks": len(chunks),
                "dimensions": args.dimensions,
                "byte_size": byte_size,
                "sha256": digest.hexdigest(),
                "embedding_model": "sentence-transformers/all-MiniLM-L6-v2",
                "chunk_size_words": args.chunk_size,
                "overlap_words": args.overlap,
                "max_sequence_length": args.max_length,
            },
            sort_keys=True,
        )
    )


if __name__ == "__main__":
    main()
