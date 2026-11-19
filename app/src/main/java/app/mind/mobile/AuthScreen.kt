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
    val english = LocalMindLanguage.current == "en"
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
        Text(if (createAccount) mindString(english, "إنشاء حساب MIND", "Create a MIND account") else mindString(english, "تسجيل الدخول", "Sign in"))
        Text(mindString(english, "تسجيل الدخول وإدارة حسابك متاحان. بيانات MIND محفوظة محلياً حالياً؛ المزامنة السحابية لم تُفعّل بعد.", "Sign-in and account management are available. MIND data is currently stored on this device; cloud sync is not enabled yet."))
        if (configError) {
            Text(mindString(english, "لم يتم تهيئة Firebase لهذا التطبيق بعد. أضف google-services.json الخاص بالحزمة app.mind.mobile وفعّل Email/Password في Firebase Console.", "Firebase is not configured for this app yet. Add the google-services.json for package app.mind.mobile and enable Email/Password in Firebase Console."))
        } else {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(mindString(english, "البريد الإلكتروني", "Email address")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(mindString(english, "كلمة المرور", "Password")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            if (!createAccount) {
                TextButton(
                    onClick = {
                        val instance = auth
                        if (instance == null) {
                            message = mindString(english, "خدمة الحساب غير متاحة حالياً.", "The account service is currently unavailable.")
                        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                            message = mindString(english, "أدخل بريدك الإلكتروني أولاً لإرسال رابط إعادة التعيين.", "Enter your email first to request a password reset.")
                        } else {
                            busy = true
                            message = null
                            instance.sendPasswordResetEmail(email).addOnCompleteListener { result ->
                                busy = false
                                message = if (result.isSuccessful) {
                                    mindString(english, "إذا كان البريد مسجلاً، فستصلك رسالة لإعادة تعيين كلمة المرور.", "If the email is registered, a password reset message will be sent.")
                                } else {
                                    mindString(english, "تعذر إرسال رابط إعادة التعيين الآن. تحقق من الاتصال وحاول لاحقاً.", "Unable to send the reset link right now. Check your connection and try again.")
                                }
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(mindString(english, "نسيت كلمة المرور؟", "Forgot password?"))
                }
            }
            auth?.currentUser?.takeIf { !it.isEmailVerified }?.let { pendingUser ->
                TextButton(
                    onClick = {
                        busy = true
                        message = null
                        pendingUser.reload().addOnCompleteListener { refresh ->
                            busy = false
                            val refreshedUser = auth?.currentUser
                            if (refresh.isSuccessful && refreshedUser?.isEmailVerified == true) {
                                onSignedIn()
                            } else if (refresh.isSuccessful) {
                                message = mindString(english, "لم يتم تأكيد البريد بعد. افتح رسالة Firebase ثم اضغط تحقق مرة أخرى.", "Email is not verified yet. Open the Firebase email, then check again.")
                            } else {
                                message = mindString(english, "تعذر التحقق من حالة البريد. تحقق من الاتصال وحاول مجدداً.", "Could not refresh verification status. Check your connection and try again.")
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(mindString(english, "تحققت من بريدي الإلكتروني", "I've verified my email"))
                }
            }
            message?.let { Text(it) }
            Button(
                onClick = {
                    val instance = auth
                    if (instance == null) {
                        message = mindString(english, "خدمة الحساب غير متاحة حالياً.", "The account service is currently unavailable.")
                    } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        message = mindString(english, "أدخل بريداً إلكترونياً صحيحاً.", "Enter a valid email address.")
                    } else if (password.length < 6) {
                        message = mindString(english, "يجب ألا تقل كلمة المرور عن 6 أحرف.", "Password must be at least 6 characters.")
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
                            if (result.isSuccessful) {
                                val user = result.result?.user
                                if (createAccount) {
                                    user?.sendEmailVerification()?.addOnCompleteListener { verification ->
                                        busy = false
                                        message = if (verification.isSuccessful) {
                                            mindString(english, "تم إنشاء الحساب. أرسلنا رابط تأكيد إلى بريدك الإلكتروني.", "Account created. We sent a verification link to your email.")
                                        } else {
                                            mindString(english, "تم إنشاء الحساب، لكن تعذر إرسال رسالة التأكيد. حاول تسجيل الدخول لاحقاً.", "Account created, but the verification email could not be sent. Try signing in later.")
                                        }
                                    } ?: run {
                                        busy = false
                                        message = mindString(english, "تم إنشاء الحساب، لكن تعذر بدء تأكيد البريد.", "Account created, but email verification could not be started.")
                                    }
                                } else if (user?.isEmailVerified == true) {
                                    onSignedIn()
                                } else {
                                    user?.sendEmailVerification()?.addOnCompleteListener {
                                        busy = false
                                        message = mindString(english, "يجب تأكيد بريدك الإلكتروني قبل المتابعة. أرسلنا رابط التأكيد مجدداً.", "Verify your email before continuing. We sent the verification link again.")
                                    } ?: run {
                                        busy = false
                                        message = mindString(english, "يجب تأكيد بريدك الإلكتروني قبل المتابعة.", "Verify your email before continuing.")
                                    }
                                }
                            } else message = when (result.exception) {
                                is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> mindString(english, "البريد أو كلمة المرور غير صحيحة.", "Incorrect email or password.")
                                is com.google.firebase.auth.FirebaseAuthUserCollisionException -> mindString(english, "هذا البريد مسجل بالفعل.", "This email is already registered.")
                                is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> mindString(english, "اختر كلمة مرور أقوى.", "Choose a stronger password.")
                                else -> mindString(english, "تعذر إكمال العملية. تحقق من الاتصال وحاول مجدداً.", "Could not complete the operation. Check your connection and try again.")
                            }
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) CircularProgressIndicator() else Text(if (createAccount) mindString(english, "إنشاء الحساب", "Create account") else mindString(english, "دخول", "Sign in"))
            }
            TextButton(onClick = {
                createAccount = !createAccount
                message = null
            }) {
                Text(if (createAccount) mindString(english, "لديك حساب؟ سجّل الدخول", "Already have an account? Sign in") else mindString(english, "إنشاء حساب جديد", "Create a new account"))
            }
        }
    }
}
