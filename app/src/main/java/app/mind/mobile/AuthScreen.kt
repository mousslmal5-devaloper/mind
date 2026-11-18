package app.mind.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

@Composable
fun AuthScreen(onSignedIn: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var createAccount by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val auth = remember {
        runCatching {
            if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseAuth.getInstance() else null
        }.getOrNull()
    }
    val configError = auth == null

    Column(
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(if (createAccount) "إنشاء حساب MIND" else "تسجيل الدخول")
        Text("حسابك الشخصي يحفظ بياناتك ويتيح مزامنتها بعد إعداد Firebase.")
        if (configError) {
            Text("لم يتم تهيئة Firebase لهذا التطبيق بعد. أضف google-services.json الخاص بالحزمة app.mind.mobile وفعّل Email/Password في Firebase Console.")
        } else {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("البريد الإلكتروني") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("كلمة المرور") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            message?.let { Text(it) }
            Button(
                onClick = {
                    val instance = auth
                    if (instance == null) {
                        message = "خدمة الحساب غير متاحة حالياً."
                    } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        message = "أدخل بريداً إلكترونياً صحيحاً."
                    } else if (password.length < 6) {
                        message = "يجب ألا تقل كلمة المرور عن 6 أحرف."
                    } else {
                        busy = true
                        message = null
                        val task = if (createAccount) {
                            instance.createUserWithEmailAndPassword(email, password)
                        } else {
                            instance.signInWithEmailAndPassword(email, password)
                        }
                        task.addOnCompleteListener { result ->
                            busy = false
                            if (result.isSuccessful) onSignedIn()
                            else message = when (result.exception) {
                                is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "البريد أو كلمة المرور غير صحيحة."
                                is com.google.firebase.auth.FirebaseAuthUserCollisionException -> "هذا البريد مسجل بالفعل."
                                is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> "اختر كلمة مرور أقوى."
                                else -> "تعذر إكمال العملية. تحقق من الاتصال وحاول مجدداً."
                            }
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) CircularProgressIndicator() else Text(if (createAccount) "إنشاء الحساب" else "دخول")
            }
            TextButton(onClick = {
                createAccount = !createAccount
                message = null
            }) {
                Text(if (createAccount) "لديك حساب؟ سجّل الدخول" else "إنشاء حساب جديد")
            }
        }
    }
}
