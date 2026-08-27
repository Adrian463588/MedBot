package com.medbot.app

import com.medbot.app.data.online.RobotsTxtPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotsTxtPolicyTest {
    @Test
    fun `most specific allow wins over disallow`() {
        val robots = """
            User-agent: *
            Disallow: /private
            Allow: /private/public
        """.trimIndent()
        assertFalse(RobotsTxtPolicy.isAllowed(robots, "/private/record"))
        assertTrue(RobotsTxtPolicy.isAllowed(robots, "/private/public/topic"))
        assertTrue(RobotsTxtPolicy.isAllowed(robots, "/health-topics/diarrhoea"))
    }
}
