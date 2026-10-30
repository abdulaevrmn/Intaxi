package com.intaxi.driver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF111113)
private val Panel = Color(0xFF202024)
private val Burgundy = Color(0xFF641D2D)
private val Muted = Color(0xFFAAAAAF)
private val Green = Color(0xFF39B878)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DriverHomeScreen()
            }
        }
    }
}

@Composable
private fun DriverHomeScreen() {
    var isOnline by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "İNTAXİ",
                        color = Color.White,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "მძღოლის აპლიკაცია",
                        color = Muted,
                        fontSize = 14.sp
                    )
                }

                Text(
                    if (isOnline) "● ონლაინ" else "● ოფლაინ",
                    color = if (isOnline) Green else Muted,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Text("დღევანდელი შემოსავალი", color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "0.00 ₾",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    SummaryValue("შეკვეთები", "0")
                    Spacer(Modifier.weight(1f))
                    SummaryValue("კილომეტრი", "0.0 კმ")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (isOnline) "თქვენ იღებთ შეკვეთებს"
                    else "მზად ხართ მუშაობისთვის?",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    if (isOnline) "აპლიკაცია აქტიურია"
                    else "ჩართეთ ონლაინ რეჟიმი შეკვეთების მისაღებად",
                    color = Muted,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = { isOnline = !isOnline },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOnline) Color(0xFF35353B)
                        else Burgundy
                    )
                ) {
                    Text(
                        if (isOnline) "ოფლაინზე გადასვლა"
                        else "ონლაინში ჩართვა",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Text(
                    "ბოლო აქტივობა",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                Text("ჯერ შეკვეთები არ გაქვთ", color = Muted, fontSize = 14.sp)
            }

            Spacer(Modifier.weight(1f))

            Text(
                "მთავარი       შეკვეთები       ისტორია       პროფილი",
                modifier = Modifier.fillMaxWidth(),
                color = Muted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: String) {
    Column {
        Text(label, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
