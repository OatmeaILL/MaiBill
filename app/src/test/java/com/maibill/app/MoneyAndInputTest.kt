package com.maibill.app

import com.maibill.app.ui.components.AmountInput
import com.maibill.app.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 金额解析与键盘连算逻辑的真实执行验证 */
class MoneyAndInputTest {

    private fun input(vararg keys: String): AmountInput {
        val i = AmountInput()
        keys.forEach { i.onKey(it) }
        return i
    }

    // ---- Money.parseFen ----

    @Test
    fun `parseFen 基本解析`() {
        assertEquals(3200L, Money.parseFen("32"))
        assertEquals(3255L, Money.parseFen("32.55"))
        assertEquals(50L, Money.parseFen("0.5"))
        assertEquals(5L, Money.parseFen("0.05"))
    }

    @Test
    fun `parseFen 非法输入返回null`() {
        assertNull(Money.parseFen(""))
        assertNull(Money.parseFen("."))
        assertNull(Money.parseFen("0"))
        assertNull(Money.parseFen("-5"))
        assertNull(Money.parseFen("abc"))
        assertNull(Money.parseFen("1.2.3"))
        assertNull(Money.parseFen("12.345")) // 三位小数 longValueExact 抛异常
    }

    @Test
    fun `parseFen 对末尾点的行为`() {
        // BigDecimal("12.") 在 JVM 上的行为：打印记录，若可解析则为 1200
        println("parseFen(\"12.\") = " + Money.parseFen("12."))
    }

    @Test
    fun `txt 千分位与两位小数`() {
        assertEquals("12,847.64", Money.txt(1284764L))
        assertEquals("0.05", Money.txt(5L))
        assertEquals("-1,000.00", Money.txt(-100000L))
        assertEquals("12847.64", Money.plain(1284764L))
        assertEquals("12,848", Money.txt0(1284764L))
    }

    // ---- AmountInput 基本输入 ----

    @Test
    fun `简单输入与两位小数`() {
        assertEquals(3200L, input("3", "2").result())
        assertEquals(3255L, input("3", "2", ".", "5", "5").result())
        assertEquals(50L, input("0", ".", "5").result())
    }

    @Test
    fun `首位0被下一个数字替换`() {
        assertEquals("5", input("0", "0", "5").text)
        assertEquals("0.5", input("0", ".", "5").text)
    }

    @Test
    fun `整数最多9位`() {
        val i = AmountInput()
        repeat(12) { i.onKey("9") }
        assertEquals(9, i.text.length)
        // 999,999,999 元 = 99,999,999,900 分
        assertEquals(99999999900L, i.result())
    }

    @Test
    fun `小数最多两位`() {
        val i = input("1", ".", "2", "3")
        i.onKey("4") // 第三位应被拒
        assertEquals("1.23", i.text)
        i.onKey("del")
        i.onKey("del")
        i.onKey("del")
        i.onKey("del") // 清空到 "1" 再删
        assertEquals("", i.text)
    }

    // ---- 连算（本轮重点回归） ----

    @Test
    fun `三项连加不丢中间项`() {
        assertEquals(1500L, input("5", "+", "3", "+", "7").result())
        assertEquals(2300L, input("1", "2", "+", "8", "+", "3").result())
    }

    @Test
    fun `连减逐项折叠`() {
        assertEquals(500L, input("1", "0", "-", "2", "-", "3").result())
    }

    @Test
    fun `加减混合`() {
        assertEquals(1100L, input("1", "0", "-", "2", "+", "3").result())
        assertEquals(1700L, input("1", "2", "+", "8", "-", "3").result())
    }

    @Test
    fun `挂起运算保存已完成部分`() {
        assertEquals(500L, input("5", "+").result())
        assertEquals(500L, input("5", "-").result())
        assertEquals(1000L, input("5", "+", "5", "-").result())
    }

    @Test
    fun `运算符未输入操作数时可切换`() {
        val i = input("5", "+", "-")
        assertEquals('-', i.op ?: ' ')
        assertEquals(500L, i.result())
    }

    @Test
    fun `结果为负视为无效`() {
        assertNull(input("3", "-", "9").result())
        assertNull(input("3", "-", "9", "+", "2").result())
    }

    @Test
    fun `空输入结果为null`() {
        assertNull(AmountInput().result())
        assertNull(input("del", "del").result())
    }

    // ---- 退格回退 ----

    @Test
    fun `退格删数字`() {
        val i = input("1", "2")
        i.onKey("del")
        assertEquals("1", i.text)
        assertEquals(100L, i.result())
    }

    @Test
    fun `退格回退运算符时还原累计值为可编辑文本`() {
        val i = input("5", "+")
        i.onKey("del")
        assertNull(i.op)
        assertNull(i.accFen)
        assertEquals("5.00", i.text)
        assertEquals(500L, i.result())
    }

    @Test
    fun `退格可逐级回退整个表达式`() {
        val i = input("5", "+", "3")
        assertEquals(800L, i.result())
        i.onKey("del") // 删 "3" → 剩挂起运算 "5＋"，可保存已完成部分 5
        assertEquals("", i.text)
        assertEquals(500L, i.result())
        i.onKey("del") // 回退运算符 → 还原 "5.00"
        assertEquals("5.00", i.text)
        assertNull(i.accFen)
        assertNull(i.op)
        assertEquals(500L, i.result())
        repeat(4) { i.onKey("del") } // "5.00" → ""
        assertEquals("", i.text)
        assertNull(i.result())
    }

    // ---- hint 与 clear ----

    @Test
    fun `连算提示`() {
        assertEquals("5.00 ＋", input("5", "+").hint())
        assertNull(input("5").hint())
        assertEquals("8.00 －", input("5", "+", "3", "-").hint())
    }

    @Test
    fun `clear重置全部状态`() {
        val i = input("5", "+", "3")
        i.clear()
        assertEquals("", i.text)
        assertNull(i.accFen)
        assertNull(i.op)
        assertNull(i.result())
    }

    @Test
    fun `边界金额不溢出`() {
        // ¥5,555,555.00 — 个人记账上限内
        val i = AmountInput()
        repeat(7) { i.onKey("5") }
        assertEquals(555555500L, i.result())
        assertTrue(Money.txt(500000000L) == "5,000,000.00")
    }

    @Test
    fun `自动记账预填`() {
        val i = AmountInput()
        i.prefill(2550L) // ¥25.50
        assertEquals("25.50", i.text)
        assertEquals(2550L, i.result())
        // 预填后仍可正常连算
        i.onKey("+")
        i.onKey("1")
        assertEquals(2650L, i.result())
    }

    @Test
    fun `支付通知金额解析`() {
        assertEquals(2550L, com.maibill.app.auto.PaymentParser.parseFen("支付成功 ¥25.50"))
        assertEquals(1200L, com.maibill.app.auto.PaymentParser.parseFen("支付宝消费12.00元"))
        assertEquals(12345600L, com.maibill.app.auto.PaymentParser.parseFen("¥123,456.00"))
        assertNull(com.maibill.app.auto.PaymentParser.parseFen("你收到一条消息"))
        assertTrue(com.maibill.app.auto.PaymentParser.shouldHandle("微信支付 支付成功 ¥25.50"))
        assertFalse(com.maibill.app.auto.PaymentParser.shouldHandle("微信红包"))
        assertFalse(com.maibill.app.auto.PaymentParser.shouldHandle("收款到账通知"))
    }

    @Test
    fun `支付成功页面金额解析`() {
        // 真实淘宝闪购账单页文本（无障碍会采到类似内容）
        val page = "淘宝闪购 -24.44 支付成功 支付时间 2026-09-30 19:43:20 " +
            "网商银行数字人民币 融柳·大铁牛螺蛳粉外卖订单 立即领取3积分 账单管理"
        assertEquals(2444L, com.maibill.app.auto.PaymentParser.parseScreenFen(page))
        assertTrue(com.maibill.app.auto.PaymentParser.shouldHandleScreen(page))
        assertEquals(2500L, com.maibill.app.auto.PaymentParser.parseScreenFen("支付成功 ¥25.00"))
        assertEquals(300L, com.maibill.app.auto.PaymentParser.parseScreenFen("付款成功 3.00元"))
        // 日期里的 -09/-30 不能被当成金额
        assertNull(
            com.maibill.app.auto.PaymentParser.parseScreenFen("支付时间 2026-09-30 19:43:20 订单号 20260930")
        )
        // 退款页面不触发
        assertFalse(com.maibill.app.auto.PaymentParser.shouldHandleScreen("退款成功 -24.44"))
    }
}
