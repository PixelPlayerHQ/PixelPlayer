package com.theveloper.pixelplay.utils

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.atilika.kuromoji.ipadic.Tokenizer
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import kotlinx.coroutines.flow.Flow
import java.lang.Character
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

// Roman Multilang 
object MultiLangRomanizer {
    private val kuromojiTokenizer: Tokenizer? by lazy {
        try {
            Thread.currentThread().contextClassLoader = MultiLangRomanizer::class.java.classLoader
            Tokenizer()
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    private val HANGUL_ROMAJA_MAP = mapOf(
        "cho" to mapOf("ᄀ" to "g", "ᄁ" to "kk", "ᄂ" to "n", "ᄃ" to "d", "ᄄ" to "tt", "ᄅ" to "r", "ᄆ" to "m", "ᄇ" to "b", "ᄈ" to "pp", "ᄉ" to "s", "ᄊ" to "ss", "ᄋ" to "", "ᄌ" to "j", "ᄍ" to "jj", "ᄎ" to "ch", "ᄏ" to "k", "ᄐ" to "t", "ᄑ" to "p", "ᄒ" to "h"),
        "jung" to mapOf("ᅡ" to "a", "ᅢ" to "ae", "ᅣ" to "ya", "ᅤ" to "yae", "ᅥ" to "eo", "ᅦ" to "e", "ᅧ" to "yeo", "ᅨ" to "ye", "ᅩ" to "o", "ᅪ" to "wa", "ᅫ" to "wae", "ᅬ" to "oe", "ᅭ" to "yo", "ᅮ" to "u", "ᅯ" to "wo", "ᅰ" to "we", "ᅱ" to "wi", "ᅲ" to "yu", "ᅳ" to "eu", "ᅴ" to "eui", "ᅵ" to "i"),
        "jong" to mapOf("ᆨ" to "k", "ᆨᄋ" to "g", "ᆨᄂ" to "ngn", "ᆨᄅ" to "ngn", "ᆨᄆ" to "ngm", "ᆨᄒ" to "kh", "ᆩ" to "kk", "ᆩᄋ" to "kg", "ᆩᄂ" to "ngn", "ᆩᄅ" to "ngn", "ᆩᄆ" to "ngm", "ᆩᄒ" to "kh", "ᆪ" to "k", "ᆪᄋ" to "ks", "ᆪᄂ" to "ngn", "ᆪᄅ" to "ngn", "ᆪᄆ" to "ngm", "ᆪᄒ" to "kch", "ᆫ" to "n", "ᆫᄅ" to "ll", "ᆬ" to "n", "ᆬᄋ" to "nj", "ᆬᄂ" to "nn", "ᆬᄅ" to "nn", "ᆬᄆ" to "nm", "ᆬㅎ" to "nch", "ᆭ" to "n", "ᆭᄋ" to "nh", "ᆭᄅ" to "nn", "ᆮ" to "t", "ᆮᄋ" to "d", "ᆮᄂ" to "nn", "ᆮᄅ" to "nn", "ᆮᄆ" to "nm", "ᆮᄒ" to "th", "ᆯ" to "l", "ᆯᄋ" to "r", "ᆯᄂ" to "ll", "ᆯᄅ" to "ll", "ᆰ" to "k", "ᆰᄋ" to "lg", "ᆰᄂ" to "ngn", "ᆰᄅ" to "ngn", "ᆰᄆ" to "ngm", "ᆰᄒ" to "lkh", "ᆱ" to "m", "ᆱᄋ" to "lm", "ᆱᄂ" to "mn", "ᆱᄅ" to "mn", "ᆱᄆ" to "mm", "ᆱᄒ" to "lmh", "ᆲ" to "p", "ᆲᄋ" to "lb", "ᆲᄂ" to "mn", "ᆲᄅ" to "mn", "ᆲᄆ" to "mm", "ᆲᄒ" to "lph", "ᆳ" to "t", "ᆳᄋ" to "ls", "ᆳᄂ" to "nn", "ᆳᄅ" to "nn", "ᆳᄆ" to "nm", "ᆳᄒ" to "lsh", "ᆴ" to "t", "ᆴᄋ" to "lt", "ᆴᄂ" to "nn", "ᆴᄅ" to "nn", "ᆴᄆ" to "nm", "ᆴᄒ" to "lth", "ᆵ" to "p", "ᆵᄋ" to "lp", "ᆵᄂ" to "mn", "ᆵᄅ" to "mn", "ᆵᄆ" to "mm", "ᆵᄒ" to "lph", "ᆶ" to "l", "ᆶᄋ" to "lh", "ᆶᄂ" to "ll", "ᆶᄅ" to "ll", "ᆶᄆ" to "lm", "ᆶᄒ" to "lh", "ᆷ" to "m", "ᆷᄅ" to "mn", "ᆸ" to "p", "ᆸᄋ" to "b", "ᆸᄂ" to "mn", "ᆸᄅ" to "mn", "ᆸᄆ" to "mm", "ᆸᄒ" to "ph", "ᆹ" to "p", "ᆹᄋ" to "ps", "ᆹᄂ" to "mn", "ᆹᄅ" to "mn", "ᆹᄆ" to "mm", "ᆹᄒ" to "psh", "ᆺ" to "t", "ᆺᄋ" to "s", "ᆺᄂ" to "nn", "ᆺᄅ" to "nn", "ᆺᄆ" to "nm", "ᆺᄒ" to "sh", "ᆻ" to "t", "ᆻᄋ" to "ss", "ᆻᄂ" to "tn", "ᆻᄅ" to "tn", "ᆻᄆ" to "nm", "ᆻᄒ" to "th", "ᆼ" to "ng", "ᆽ" to "t", "ᆽᄋ" to "j", "ᆽᄂ" to "nn", "ᆽᄅ" to "nn", "ᆽᄆ" to "nm", "ᆽᄒ" to "ch", "ᆾ" to "t", "ᆾᄋ" to "ch", "ᆾᄂ" to "nn", "ᆾᄅ" to "nn", "ᆾᄆ" to "nm", "ᆾᄒ" to "ch", "ᆿ" to "k", "ᆿᄋ" to "k", "ᆿᄂ" to "ngn", "ᆿᄅ" to "ngn", "ᆿᄆ" to "ngm", "ᆿᄒ" to "kh", "ᇀ" to "t", "ᇀᄋ" to "t", "ᇀᄂ" to "nn", "ᇀᄅ" to "nn", "ᇀᄆ" to "nm", "ᇀᄒ" to "th", "ᇁ" to "p", "ᇁᄋ" to "p", "ᇁᄂ" to "mn", "ᇁᄅ" to "mn", "ᇁᄆ" to "mm", "ᇁᄒ" to "ph", "ᇂ" to "t", "ᇂᄋ" to "h", "ᇂᄂ" to "nn", "ᇂᄅ" to "nn", "ᇂᄆ" to "mm", "ᇂᄒ" to "t", "ᇂᄀ" to "k")
    )

    private val DEVANAGARI_ROMAJI_MAP = mapOf(
        "अ" to "a", "आ" to "aa", "इ" to "i", "ई" to "ee", "उ" to "u", "ऊ" to "oo", "ऋ" to "ri", "ए" to "e", "ऐ" to "ai", "ओ" to "o", "औ" to "au", "क" to "k", "ख" to "kh", "ग" to "g", "घ" to "gh", "ङ" to "ng", "च" to "ch", "छ" to "chh", "ज" to "j", "झ" to "jh", "ञ" to "ny", "ट" to "t", "ठ" to "th", "ड" to "d", "ढ" to "dh", "ण" to "n", "त" to "t", "थ" to "th", "द" to "d", "ध" to "dh", "न" to "n", "प" to "p", "फ" to "ph", "ब" to "b", "भ" to "bh", "म" to "m", "य" to "y", "र" to "r", "ल" to "l", "व" to "v", "श" to "sh", "ष" to "sh", "स" to "s", "ह" to "h", "क्ष" to "ksh", "त्र" to "tr", "ज्ञ" to "gy", "श्र" to "shr", "ा" to "aa", "ि" to "i", "ी" to "ee", "ु" to "u", "ू" to "oo", "ृ" to "ri", "े" to "e", "ै" to "ai", "ो" to "o", "ौ" to "au", "ं" to "n", "ः" to "h", "ँ" to "n", "़" to "", "्" to "", "०" to "0", "१" to "1", "२" to "2", "३" to "3", "४" to "4", "५" to "5", "६" to "6", "७" to "7", "८" to "8", "९" to "9", "ॐ" to "Om", "ऽ" to "", "क़" to "q", "ख़" to "kh", "ग़" to "g", "ज़" to "z", "ड़" to "r", "ढ़" to "rh", "फ़" to "f", "य़" to "y", "क\u093C" to "q", "ख\u093C" to "kh", "ग\u093C" to "g", "ज\u093C" to "z", "ड\u093C" to "r", "ढ\u093C" to "rh", "फ\u093C" to "f", "य\u093C" to "y"
    )

    private val GURMUKHI_ROMAJI_MAP = mapOf(
        "ੳ" to "o", "ਅ" to "a", "ੲ" to "e", "ਸ" to "s", "ਹ" to "h", "ਕ" to "k", "ਖ" to "kh", "ਗ" to "g", "ਘ" to "gh", "ਙ" to "ng", "ਚ" to "ch", "ਛ" to "chh", "ਜ" to "j", "ਝ" to "jh", "ਞ" to "ny", "ਟ" to "t", "ਠ" to "th", "ਡ" to "d", "ਢ" to "dh", "ਣ" to "n", "ਤ" to "t", "ਥ" to "th", "ਦ" to "d", "ਧ" to "dh", "ਨ" to "n", "ਪ" to "p", "ਫ" to "ph", "ਬ" to "b", "ਭ" to "bh", "ਮ" to "m", "ਯ" to "y", "ਰ" to "r", "ਲ" to "l", "ਵ" to "v", "ੜ" to "r", "ਸ਼" to "sh", "ਖ਼" to "kh", "ਗ਼" to "g", "ਜ਼" to "z", "ਫ਼" to "f", "ਲ਼" to "l", "ਾ" to "aa", "ਿ" to "i", "ੀ" to "ee", "ੁ" to "u", "ੂ" to "oo", "ੇ" to "e", "ੈ" to "ai", "ੋ" to "o", "ੌ" to "au", "ੰ" to "n", "ਂ" to "n", "ੱ" to "", "੍" to "", "਼" to "", "ੴ" to "Ek Onkar", "੦" to "0", "੧" to "1", "੨" to "2", "੩" to "3", "੪" to "4", "੫" to "5", "੬" to "6", "੭" to "7", "੮" to "8", "੯" to "9"
    )

    private val GENERAL_CYRILLIC_ROMAJI_MAP = mapOf(
        "А" to "A", "Б" to "B", "В" to "V", "Г" to "G", "Ґ" to "G", "Д" to "D", "Ѓ" to "Ǵ", "Ђ" to "Đ", "Е" to "E", "Ё" to "Yo", "Є" to "Ye", "Ж" to "Zh", "З" to "Z", "Ѕ" to "Dz", "И" to "I", "І" to "I", "Ї" to "Yi", "Й" to "Y", "Ј" to "Y", "К" to "K", "Л" to "L", "Љ" to "Ly", "М" to "M", "Н" to "N", "Њ" to "Ny", "О" to "O", "П" to "P", "Р" to "R", "С" to "S", "Т" to "T", "Ћ" to "Ć", "У" to "U", "Ў" to "Ŭ", "Ф" to "F", "Х" to "Kh", "Ц" to "Ts", "Ч" to "Ch", "Џ" to "Dž", "Ш" to "Sh", "Щ" to "Shch", "Ъ" to "ʺ", "Ы" to "Y", "Ь" to "ʹ", "Э" to "E", "Ю" to "Yu", "Я" to "Ya",
        "а" to "a", "б" to "b", "в" to "v", "г" to "g", "ґ" to "g", "д" to "d", "ѓ" to "ǵ", "ђ" to "đ", "е" to "e", "ё" to "yo", "є" to "ye", "ж" to "zh", "з" to "z", "ѕ" to "dz", "и" to "i", "і" to "i", "ї" to "yi", "й" to "y", "ј" to "y", "к" to "k", "л" to "l", "љ" to "ly", "м" to "m", "н" to "n", "њ" to "ny", "о" to "o", "п" to "p", "р" to "r", "с" to "s", "т" to "t", "ћ" to "ć", "у" to "u", "ў" to "ŭ", "ф" to "f", "х" to "kh", "ц" to "ts", "ч" to "ch", "џ" to "dž", "ш" to "sh", "щ" to "shch", "ъ" to "ʺ", "ы" to "y", "ь" to "ʹ", "э" to "e", "ю" to "yu", "я" to "ya"
    )

    private val RUSSIAN_ROMAJI_MAP = mapOf("ого" to "ovo", "Ого" to "Ovo", "его" to "evo", "Его" to "Evo")
    private val UKRAINIAN_ROMAJI_MAP = mapOf("Г" to "H", "г" to "h", "Ґ" to "G", "ґ" to "g", "Є" to "Ye", "є" to "ye", "І" to "I", "і" to "i", "Ї" to "Yi", "ї" to "yi")
    private val SERBIAN_ROMAJI_MAP = mapOf("Ж" to "Ž", "Љ" to "Lj", "Њ" to "Nj", "Ц" to "C", "Ч" to "Č", "Џ" to "Dž", "Ш" to "Š", "Х" to "H", "ж" to "ž", "љ" to "lj", "њ" to "nj", "ц" to "c", "ч" to "č", "џ" to "dž", "ш" to "š", "х" to "h")
    private val BULGARIAN_ROMAJI_MAP = mapOf("Ж" to "Zh", "Ц" to "Ts", "Ч" to "Ch", "Ш" to "Sh", "Щ" to "Sht", "Ъ" to "A", "Ь" to "Y", "Ю" to "Yu", "Я" to "Ya", "ж" to "zh", "ц" to "ts", "ч" to "ch", "ш" to "sh", "щ" to "sht", "ъ" to "a", "ь" to "y", "ю" to "yu", "я" to "ya")
    private val BELARUSIAN_ROMAJI_MAP = mapOf("Г" to "H", "г" to "h", "Ў" to "W", "ў" to "w")
    private val KYRGYZ_ROMAJI_MAP = mapOf("Ү" to "Ü", "ү" to "ü", "Ы" to "Y", "ы" to "y")
    private val MACEDONIAN_ROMAJI_MAP = mapOf("Ѓ" to "Gj", "Ѕ" to "Dz", "И" to "I", "Ј" to "J", "Љ" to "Lj", "Њ" to "Nj", "Ќ" to "Kj", "Џ" to "Dž", "Ч" to "Č", "Ш" to "Sh", "Ж" to "Zh", "Ц" to "C", "Х" to "H", "ѓ" to "gj", "ѕ" to "dz", "и" to "i", "ј" to "j", "љ" to "lj", "њ" to "nj", "ќ" to "kj", "џ" to "dž", "ч" to "č", "ш" to "sh", "ж" to "zh", "ц" to "c", "х" to "h")

    private val RUSSIAN_CYRILLIC_LETTERS = setOf("А", "Б", "В", "Г", "Д", "Е", "Ё", "Ж", "З", "И", "Й", "К", "Л", "М", "Н", "О", "П", "Р", "С", "Т", "У", "Ф", "Х", "Ц", "Ч", "Ш", "Щ", "Ъ", "Ы", "Ь", "Э", "Ю", "Я", "а", "б", "в", "г", "д", "е", "ё", "ж", "з", "и", "й", "к", "л", "м", "н", "о", "п", "р", "с", "т", "у", "ф", "х", "ц", "ч", "ш", "щ", "ъ", "ы", "ь", "э", "ю", "я")
    private val UKRAINIAN_CYRILLIC_LETTERS = setOf("А", "Б", "В", "Г", "Ґ", "Д", "Е", "Є", "Ж", "З", "И", "І", "Ї", "Й", "К", "Л", "М", "Н", "О", "П", "Р", "С", "Т", "У", "Ф", "Х", "Ц", "Ч", "Ш", "Щ", "Ь", "Ю", "Я", "а", "б", "в", "г", "ґ", "д", "е", "є", "ж", "з", "и", "і", "ї", "й", "к", "л", "м", "н", "о", "п", "р", "с", "т", "у", "ф", "х", "ц", "ч", "ш", "щ", "ь", "ю", "я")
    private val SERBIAN_CYRILLIC_LETTERS = setOf("А", "Б", "В", "Г", "Д", "Ђ", "Е", "Ж", "З", "И", "Ј", "К", "Л", "Љ", "М", "Н", "Њ", "О", "П", "Р", "С", "Т", "Ћ", "У", "Ф", "Х", "Ц", "Ч", "Џ", "Ш", "а", "б", "в", "г", "д", "ђ", "е", "ж", "з", "и", "ј", "к", "л", "љ", "м", "н", "њ", "о", "п", "р", "с", "т", "ћ", "у", "ф", "х", "ц", "ч", "џ", "ш")
    private val BULGARIAN_CYRILLIC_LETTERS = setOf("А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "Й", "К", "Л", "М", "Н", "О", "П", "Р", "С", "Т", "У", "Ф", "Х", "Ц", "Ч", "Ш", "Щ", "Ъ", "Ь", "Ю", "Я", "а", "б", "в", "г", "д", "е", "ж", "з", "и", "й", "к", "л", "м", "н", "о", "п", "р", "с", "т", "у", "ф", "х", "ц", "ч", "ш", "щ", "ъ", "ь", "ю", "я")
    private val BELARUSIAN_CYRILLIC_LETTERS = setOf("А", "Б", "В", "Г", "Д", "Е", "Ё", "Ж", "З", "І", "Й", "К", "Л", "М", "Н", "О", "П", "Р", "С", "Т", "У", "Ў", "Ф", "Х", "Ц", "Ч", "Ш", "Ь", "Ю", "Я", "Ы", "Э", "а", "б", "в", "г", "д", "е", "ё", "ж", "з", "і", "й", "к", "л", "м", "н", "о", "п", "р", "с", "т", "у", "ў", "ф", "х", "ц", "ч", "ш", "ь", "ю", "я", "ы", "э")
    private val KYRGYZ_CYRILLIC_LETTERS = setOf("А", "Б", "В", "Г", "Д", "Е", "Ё", "Ж", "З", "И", "Й", "К", "Л", "М", "Н", "Ң", "О", "Ө", "П", "Р", "С", "Т", "У", "Ү", "Ф", "Х", "Ц", "Ч", "Ш", "Щ", "Ъ", "Ы", "Ь", "Э", "Ю", "Я", "а", "б", "в", "г", "д", "е", "ё", "ж", "з", "и", "й", "к", "л", "м", "н", "ң", "о", "ө", "п", "р", "с", "т", "у", "ү", "ф", "х", "ц", "ч", "ш", "щ", "ъ", "ы", "ь", "э", "ю", "я")
    private val MACEDONIAN_CYRILLIC_LETTERS = setOf("А", "Б", "В", "Г", "Д", "Ѓ", "Е", "Ж", "З", "Ѕ", "И", "Ј", "К", "Л", "Љ", "М", "Н", "Њ", "О", "П", "Р", "С", "Т", "Ќ", "У", "Ф", "Х", "Ц", "Ч", "Џ", "Ш", "а", "б", "в", "г", "д", "ѓ", "е", "ж", "з", "ѕ", "и", "ј", "к", "л", "љ", "м", "н", "њ", "о", "п", "р", "с", "т", "ќ", "у", "ф", "х", "ц", "ч", "џ", "ш")

    private val UKRAINIAN_SPECIFIC_CYRILLIC_LETTERS = setOf("Ґ", "ґ", "Є", "є", "І", "і", "Ї", "ї")
    private val SERBIAN_SPECIFIC_CYRILLIC_LETTERS = setOf("Ђ", "ђ", "Ј", "ј", "Љ", "љ", "Њ", "њ", "Ћ", "ћ", "Џ", "џ")
    private val BELARUSIAN_SPECIFIC_CYRILLIC_LETTERS = setOf("Ў", "ў", "І", "і")
    private val KYRGYZ_SPECIFIC_CYRILLIC_LETTERS = setOf("Ң", "ң", "Ө", "ө", "Ү", "ү")
    private val MACEDONIAN_SPECIFIC_CYRILLIC_LETTERS = setOf("Ѓ", "ѓ", "Ѕ", "ѕ", "Ќ", "ќ")

    fun isJapanese(text: String): Boolean {
        // Detect Hiragana or Katakana
        if (text.any { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' }) return true
        
        // Treat Kanji-only lines as Japanese (prioritized over Chinese in the main parsing loop)
        return text.any { it in '\u4E00'..'\u9FFF' }
    }
    fun isKorean(text: String) = text.any { it in '\uAC00'..'\uD7A3' }
    fun isHindi(text: String) = text.any { it in '\u0900'..'\u097F' }
    fun isPunjabi(text: String) = text.any { it in '\u0A00'..'\u0A7F' }
    fun isCyrillic(text: String) = text.any { it in '\u0400'..'\u04FF' }
    fun isChinese(text: String) = text.any { it in '\u4E00'..'\u9FFF' }

    fun isScriptThatNeedsRomanization(text: String): Boolean {
        return text.any { char ->
            val code = char.code
            code in 0x3040..0x309F || // Hiragana
            code in 0x30A0..0x30FF || // Katakana
            code in 0x4E00..0x9FFF || // CJK Unified Ideographs (Chinese)
            code in 0xAC00..0xD7A3 || // Hangul (Korean)
            code in 0x0900..0x097F || // Devanagari (Hindi)
            code in 0x0A00..0x0A7F || // Gurmukhi (Punjabi)
            code in 0x0400..0x04FF    // Cyrillic
        }
    }

    private fun isRussian(text: String) = text.any { RUSSIAN_CYRILLIC_LETTERS.contains(it.toString()) } && text.all { RUSSIAN_CYRILLIC_LETTERS.contains(it.toString()) || !it.toString().matches("[\\u0400-\\u04FF]".toRegex()) }
    private fun isUkrainian(text: String) = text.any { UKRAINIAN_CYRILLIC_LETTERS.contains(it.toString()) || UKRAINIAN_SPECIFIC_CYRILLIC_LETTERS.contains(it.toString()) } && text.all { UKRAINIAN_CYRILLIC_LETTERS.contains(it.toString()) || UKRAINIAN_SPECIFIC_CYRILLIC_LETTERS.contains(it.toString()) || !it.toString().matches("[\\u0400-\\u04FF]".toRegex()) }
    private fun isSerbian(text: String) = text.any { SERBIAN_CYRILLIC_LETTERS.contains(it.toString()) || SERBIAN_SPECIFIC_CYRILLIC_LETTERS.contains(it.toString()) } && text.all { SERBIAN_CYRILLIC_LETTERS.contains(it.toString()) || SERBIAN_SPECIFIC_CYRILLIC_LETTERS.contains(it.toString()) || !it.toString().matches("[\\u0400-\\u04FF]".toRegex()) }
    private fun isBulgarian(text: String) = text.any { BULGARIAN_CYRILLIC_LETTERS.contains(it.toString()) } && text.all { BULGARIAN_CYRILLIC_LETTERS.contains(it.toString()) || !it.toString().matches("[\\u0400-\\u04FF]".toRegex()) }
    private fun isBelarusian(text: String) = text.any { BELARUSIAN_CYRILLIC_LETTERS.contains(it.toString()) || BELARUSIAN_SPECIFIC_CYRILLIC_LETTERS.contains(it.toString()) } && text.all { BELARUSIAN_CYRILLIC_LETTERS.contains(it.toString()) || BELARUSIAN_SPECIFIC_CYRILLIC_LETTERS.contains(it.toString()) || !it.toString().matches("[\\u0400-\\u04FF]".toRegex()) }
    private fun isKyrgyz(text: String) = text.any { KYRGYZ_CYRILLIC_LETTERS.contains(it.toString()) || KYRGYZ_SPECIFIC_CYRILLIC_LETTERS.contains(it.toString()) } && text.all { KYRGYZ_CYRILLIC_LETTERS.contains(it.toString()) || KYRGYZ_SPECIFIC_CYRILLIC_LETTERS.contains(it.toString()) || !it.toString().matches("[\\u0400-\\u04FF]".toRegex()) }
    private fun isMacedonian(text: String) = text.any { MACEDONIAN_CYRILLIC_LETTERS.contains(it.toString()) || MACEDONIAN_SPECIFIC_CYRILLIC_LETTERS.contains(it.toString()) } && text.all { MACEDONIAN_CYRILLIC_LETTERS.contains(it.toString()) || MACEDONIAN_SPECIFIC_CYRILLIC_LETTERS.contains(it.toString()) || !it.toString().matches("[\\u0400-\\u04FF]".toRegex()) }

    fun romanizeJapanese(japaneseText: String): String? {
        val tokenizer = kuromojiTokenizer ?: return null

        return try {
            val tokens = tokenizer.tokenize(japaneseText)
            val katakanaBuilder = StringBuilder()

            // Handle irregular readings and token merging
            val processedReadings = mutableListOf<Triple<String, String, String>>() // <Reading, POS1, POS2>
            var i = 0
            while (i < tokens.size) {
                val token = tokens[i]
                val nextToken = if (i + 1 < tokens.size) tokens[i + 1] else null
                
                // Merge irregular counts (e.g., hitori, futari)
                if (token.surface == "一" && nextToken?.surface == "人") {
                    processedReadings.add(Triple("ヒトリ", "名詞", "一般"))
                    i += 2
                    continue
                }
                if (token.surface == "二" && nextToken?.surface == "人") {
                    processedReadings.add(Triple("フタリ", "名詞", "一般"))
                    i += 2
                    continue
                }
                
                val reading = token.reading
                val surface = token.surface
                val pos1 = token.partOfSpeechLevel1
                val pos2 = token.partOfSpeechLevel2

                // Handle special particle pronunciations (kunyomi vs particles)
                val readingText = if (pos1 == "助詞") {
                    when (surface) {
                        "は" -> "ワ"
                        "へ" -> "エ"
                        "を" -> "オ"
                        else -> if (!reading.isNullOrBlank() && reading != "*") reading else surface
                    }
                } else {
                    if (!reading.isNullOrBlank() && reading != "*") reading else surface
                }
                processedReadings.add(Triple(readingText, pos1, pos2))
                i++
            }

            processedReadings.forEachIndexed { index, pair ->
                val readingText = pair.first
                val pos1 = pair.second
                val pos2 = pair.third

                val prevReading = if (index > 0) processedReadings[index - 1].first else ""
                val prevEndsWithSokuon = prevReading.endsWith("ッ")
                
                // Intelligent spacing:
                // - Join inflections (after sokuon 'ッ') and auxiliaries (-ta, -masu)
                // - Add spaces for readability before particles and content words
                val needsNoSpace = index == 0 || prevEndsWithSokuon || pos1 == "助動詞" || pos2 == "接尾" || pos1 == "記号"

                if (!needsNoSpace) {
                    katakanaBuilder.append(" ")
                }
                katakanaBuilder.append(readingText)
            }

            val katakanaText = katakanaBuilder.toString().replace("\\s+".toRegex(), " ").trim()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val transliterator = android.icu.text.Transliterator.getInstance("Katakana-Latin; Lower")
                transliterator.transliterate(katakanaText)
            } else {
                katakanaText
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    fun romanizeChinese(text: String): String? {
        return try {
            val format = HanyuPinyinOutputFormat().apply {
                caseType = HanyuPinyinCaseType.LOWERCASE
                toneType = HanyuPinyinToneType.WITHOUT_TONE
            }

            val sb = java.lang.StringBuilder()

            for (c in text) {
                if (c.toString().matches(Regex("[\\u4E00-\\u9FA5]"))) {
                    val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, format)
                    if (pinyinArray != null && pinyinArray.isNotEmpty()) {
                        sb.append(pinyinArray[0]).append(" ")
                    } else {
                        sb.append(c)
                    }
                } else {
                    sb.append(c)
                }
            }

            sb.toString().replace(Regex("\\s+"), " ").trim()
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    fun romanizeKorean(text: String): String {
        val romajaBuilder = java.lang.StringBuilder()
        var prevFinal: String? = null
        for (i in text.indices) {
            val char = text[i]
            if (char in '\uAC00'..'\uD7A3') {
                val syllableIndex = char.code - 0xAC00
                val choIndex = syllableIndex / (21 * 28)
                val jungIndex = (syllableIndex % (21 * 28)) / 28
                val jongIndex = syllableIndex % 28

                val choChar = (0x1100 + choIndex).toChar().toString()
                val jungChar = (0x1161 + jungIndex).toChar().toString()
                val jongChar = if (jongIndex == 0) null else (0x11A7 + jongIndex).toChar().toString()

                if (prevFinal != null) {
                    val contextKey = prevFinal + choChar
                    val jong = HANGUL_ROMAJA_MAP["jong"]?.get(contextKey) ?: HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal
                    romajaBuilder.append(jong)
                }

                val cho = HANGUL_ROMAJA_MAP["cho"]?.get(choChar) ?: choChar
                val jung = HANGUL_ROMAJA_MAP["jung"]?.get(jungChar) ?: jungChar
                romajaBuilder.append(cho).append(jung)
                prevFinal = jongChar
            } else {
                if (prevFinal != null) {
                    romajaBuilder.append(HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal)
                    prevFinal = null
                }
                romajaBuilder.append(char)
            }
        }
        if (prevFinal != null) romajaBuilder.append(HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal)
        return romajaBuilder.toString()
    }

    fun romanizeHindi(text: String): String {
        val sb = java.lang.StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            var consumed = false
            if (i + 1 < text.length) {
                val twoCharCandidate = text.substring(i, i + 2)
                val mappedTwoChar = DEVANAGARI_ROMAJI_MAP[twoCharCandidate]
                if (mappedTwoChar != null) {
                    sb.append(mappedTwoChar)
                    i += 2
                    consumed = true
                }
            }
            if (!consumed) {
                val charStr = text[i].toString()
                sb.append(DEVANAGARI_ROMAJI_MAP[charStr] ?: charStr)
                i += 1
            }
        }
        return sb.toString()
    }

    fun romanizePunjabi(text: String): String {
        val sb = java.lang.StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val char = text[i]
            var consumed = false
            if (char == '\u0A71') {
                if (i + 1 < text.length) {
                    val nextCharStr = text[i+1].toString()
                    val nextMapped = GURMUKHI_ROMAJI_MAP[nextCharStr]
                    if (nextMapped != null && nextMapped.isNotEmpty()) {
                        sb.append(nextMapped[0])
                    }
                }
                i++
                continue
            }
            if (i + 1 < text.length) {
                val twoCharCandidate = text.substring(i, i + 2)
                val mappedTwoChar = GURMUKHI_ROMAJI_MAP[twoCharCandidate]
                if (mappedTwoChar != null) {
                    sb.append(mappedTwoChar)
                    i += 2
                    consumed = true
                }
            }
            if (!consumed) {
                val str = char.toString()
                sb.append(GURMUKHI_ROMAJI_MAP[str] ?: str)
                i++
            }
        }
        return sb.toString()
    }

    fun romanizeCyrillic(text: String): String? {
        if (text.isEmpty()) return null
        val cyrillicChars = text.filter { it in '\u0400'..'\u04FF' }
        if (cyrillicChars.isEmpty() || (cyrillicChars.length == 1 && (cyrillicChars[0] == 'е' || cyrillicChars[0] == 'Е'))) return null

        return when {
            isRussian(text) -> processCyrillicWordByWord(text, RUSSIAN_ROMAJI_MAP, isRussian = true)
            isUkrainian(text) -> processUkrainian(text)
            isSerbian(text) -> processCyrillicWordByWord(text, SERBIAN_ROMAJI_MAP)
            isBulgarian(text) -> processCyrillicWordByWord(text, BULGARIAN_ROMAJI_MAP)
            isBelarusian(text) -> processBelarusian(text)
            isKyrgyz(text) -> processCyrillicWordByWord(text, KYRGYZ_ROMAJI_MAP)
            isMacedonian(text) -> processCyrillicWordByWord(text, MACEDONIAN_ROMAJI_MAP)
            else -> processCyrillicWordByWord(text, emptyMap())
        }
    }

    private fun processCyrillicWordByWord(text: String, specificMap: Map<String, String>, isRussian: Boolean = false): String {
        val romajiBuilder = java.lang.StringBuilder(text.length)
        val words = text.split("((?<=\\s|[.,!?;])|(?=\\s|[.,!?;]))".toRegex()).filter { it.isNotEmpty() }

        words.forEachIndexed { _, word ->
            if (word.matches("[.,!?;]".toRegex()) || word.isBlank()) {
                romajiBuilder.append(word)
            } else {
                var charIndex = 0
                while (charIndex < word.length) {
                    var consumed = false
                    if (isRussian && charIndex + 2 < word.length) {
                        val threeChar = word.substring(charIndex, charIndex + 3)
                        if (specificMap.containsKey(threeChar)) {
                            romajiBuilder.append(specificMap[threeChar])
                            charIndex += 3
                            consumed = true
                        }
                    }
                    if (!consumed) {
                        val charStr = word[charIndex].toString()
                        if (isRussian && (charStr == "е" || charStr == "Е") && (charIndex == 0 || word[charIndex - 1].isWhitespace())) {
                            romajiBuilder.append(if (charStr == "е") "ye" else "Ye")
                        } else {
                            romajiBuilder.append(specificMap[charStr] ?: GENERAL_CYRILLIC_ROMAJI_MAP[charStr] ?: charStr)
                        }
                        charIndex += 1
                    }
                }
            }
        }
        return romajiBuilder.toString()
    }

    private fun processUkrainian(text: String): String {
        val romajiBuilder = java.lang.StringBuilder(text.length)
        val words = text.split("((?<=\\s|[.,!?;])|(?=\\s|[.,!?;]))".toRegex()).filter { it.isNotEmpty() }

        words.forEach { word ->
            if (word.matches("[.,!?;]".toRegex()) || word.isBlank()) {
                romajiBuilder.append(word)
            } else {
                var charIndex = 0
                while (charIndex < word.length) {
                    val charStr = word[charIndex].toString()
                    var processed = false
                    if (charIndex > 0 && word[charIndex - 1].isLetter() && !"АаЕеЄєИиІіЇїОоУуЮюЯяЫыЭэ".contains(word[charIndex - 1])) {
                        if (charStr == "Ю") { romajiBuilder.append("Iu"); processed = true }
                        else if (charStr == "ю") { romajiBuilder.append("iu"); processed = true }
                        else if (charStr == "Я") { romajiBuilder.append("Ia"); processed = true }
                        else if (charStr == "я") { romajiBuilder.append("ia"); processed = true }
                    }
                    if (!processed) {
                        romajiBuilder.append(UKRAINIAN_ROMAJI_MAP[charStr] ?: GENERAL_CYRILLIC_ROMAJI_MAP[charStr] ?: charStr)
                    }
                    charIndex++
                }
            }
        }
        return romajiBuilder.toString()
    }

    private fun processBelarusian(text: String): String {
        val romajiBuilder = java.lang.StringBuilder(text.length)
        val words = text.split("((?<=\\s|[.,!?;])|(?=\\s|[.,!?;]))".toRegex()).filter { it.isNotEmpty() }

        words.forEach { word ->
            if (word.matches("[.,!?;]".toRegex()) || word.isBlank()) {
                romajiBuilder.append(word)
            } else {
                var charIndex = 0
                while (charIndex < word.length) {
                    val charStr = word[charIndex].toString()
                    if ((charStr == "е" || charStr == "Е") && (charIndex == 0 || word[charIndex - 1].isWhitespace())) {
                        romajiBuilder.append(if (charStr == "е") "ye" else "Ye")
                    } else {
                        romajiBuilder.append(BELARUSIAN_ROMAJI_MAP[charStr] ?: GENERAL_CYRILLIC_ROMAJI_MAP[charStr] ?: charStr)
                    }
                    charIndex += 1
                }
            }
        }
        return romajiBuilder.toString()
    }
}

private fun String.capitalizeFirstLetter(): String {
    if (this.isEmpty()) return this
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
}

object LyricsUtils {

    private val LRC_LINE_REGEX = Pattern.compile("^\\[(\\d{2}):(\\d{2})[.:](\\d{2,3})](.*)$")
    private val LRC_WORD_REGEX = Pattern.compile("<(\\d{2}):(\\d{2})[.:](\\d{2,3})>([^<]*)")
    private val LRC_WORD_TAG_REGEX = Regex("<\\d{2}:\\d{2}[.:]\\d{2,3}>")
    private val LRC_WORD_SPLIT_REGEX = Regex("(?=<\\d{2}:\\d{2}[.:]\\d{2,3}>)")
    private val LRC_TIMESTAMP_TAG_REGEX = Regex("\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?]")
    private val TRANSLATION_CREDIT_REGEX = Regex("^\\s*by\\s*[:：].+", RegexOption.IGNORE_CASE)
    private val LRC_METADATA_PATTERN = Pattern.compile("^\\[[a-zA-Z]+:.*]$")

    /**
     * Parsea un String que contiene una letra en formato LRC o texto plano.
     * @param lyricsText El texto de la letra a procesar.
     * @return Un objeto Lyrics con las listas 'plain' o 'synced' pobladas.
     */
    fun parseLyrics(lyricsText: String?): Lyrics {
        if (lyricsText.isNullOrEmpty()) {
            return Lyrics(plain = emptyList(), synced = emptyList())
        }

        val syncedLines = mutableListOf<SyncedLine>()
        val plainLines = mutableListOf<String>()
        var isSynced = false

        lyricsText.lines().forEach { rawLine ->
            val line = sanitizeLrcLine(rawLine)
            if (line.isEmpty() || LRC_METADATA_PATTERN.matcher(line).matches()) return@forEach

            val lineMatcher = LRC_LINE_REGEX.matcher(line)
            if (lineMatcher.matches()) {
                isSynced = true
                val minutes = lineMatcher.group(1)?.toLong() ?: 0
                val seconds = lineMatcher.group(2)?.toLong() ?: 0
                val fraction = lineMatcher.group(3)?.toLong() ?: 0
                val textWithTags = stripFormatCharacters(lineMatcher.group(4)?.trim() ?: "")
                val text = stripLrcTimestamps(textWithTags)

                val millis = if (lineMatcher.group(3)?.length == 2) fraction * 10 else fraction
                val lineTimestamp = minutes * 60 * 1000 + seconds * 1000 + millis

                // Enhanced word-by-word parsing
                if (text.contains(LRC_WORD_TAG_REGEX)) {
                    val words = mutableListOf<SyncedWord>()
                    val parts = text.split(LRC_WORD_SPLIT_REGEX)
                    val displayText = LRC_WORD_TAG_REGEX.replace(text, "")
                    var pendingWordBoundary = false

                    for (part in parts) {
                        if (part.isEmpty()) continue
                        val wordMatcher = LRC_WORD_REGEX.matcher(part)
                        if (wordMatcher.find()) {
                            val wordMinutes = wordMatcher.group(1)?.toLong() ?: 0
                            val wordSeconds = wordMatcher.group(2)?.toLong() ?: 0
                            val wordFraction = wordMatcher.group(3)?.toLong() ?: 0
                            val wordText = stripFormatCharacters(wordMatcher.group(4) ?: "")
                            val timedWordTextRaw = wordText
                                .substringBefore('\n')
                                .substringBefore('\r')
                                .substringBefore("\\n")
                                .substringBefore("\\r")
                            val startsNewWord = words.isEmpty() ||
                                pendingWordBoundary ||
                                timedWordTextRaw.firstOrNull()?.isWhitespace() == true
                            val timedWordText = timedWordTextRaw.trim()
                            pendingWordBoundary = timedWordTextRaw.lastOrNull()?.isWhitespace() == true
                            val wordMillis = if (wordMatcher.group(3)?.length == 2) wordFraction * 10 else wordFraction
                            val wordTimestamp = wordMinutes * 60 * 1000 + wordSeconds * 1000 + wordMillis
                            if (timedWordText.isNotEmpty()) {
                                words.add(
                                    SyncedWord(
                                        time = wordTimestamp.toInt(),
                                        word = timedWordText,
                                        startsNewWord = startsNewWord
                                    )
                                )
                            }
                        } else {
                            // Preserve only leading untagged text as a timed word.
                            // Trailing untagged chunks (e.g. inline translations) should remain visible in line text
                            // but must not steal word highlight timing.
                            if (words.isEmpty()) {
                                val leading = stripFormatCharacters(part)
                                val startsNewWord = pendingWordBoundary || leading.firstOrNull()?.isWhitespace() == true
                                val visibleLeading = leading.trim()
                                pendingWordBoundary = leading.lastOrNull()?.isWhitespace() == true
                                if (visibleLeading.isNotEmpty()) {
                                    words.add(
                                        SyncedWord(
                                            time = lineTimestamp.toInt(),
                                            word = visibleLeading,
                                            startsNewWord = words.isEmpty() || startsNewWord
                                        )
                                    )
                                }
                            } else if (part.any { it.isWhitespace() }) {
                                pendingWordBoundary = true
                            }
                        }
                    }

                    if (words.isNotEmpty()) {
                        syncedLines.add(SyncedLine(lineTimestamp.toInt(), displayText, words))
                    } else {
                        syncedLines.add(SyncedLine(lineTimestamp.toInt(), displayText))
                    }
                } else {
                    syncedLines.add(SyncedLine(lineTimestamp.toInt(), text))
                }
            } else {
                // línea SIN timestamp
                val stripped = stripLrcTimestamps(stripFormatCharacters(line))
                // Si ya detectamos que el archivo tiene sincronización y ya existe
                // al menos una SyncedLine, tratamos esta línea como continuación
                // de la anterior
                if (isSynced && syncedLines.isNotEmpty()) {
                    val last = syncedLines.removeAt(syncedLines.lastIndex)
                    // Mantenemos el texto previo y añadimos la nueva línea con un salto de línea.
                    val mergedLineText = if (last.line.isEmpty()) {
                        stripped
                    } else {
                        last.line + "\n" + stripped
                    }
                    // Conservamos la lista de palabras sincronizadas si existía.
                    val merged = if (last.words?.isNotEmpty() == true) {
                        SyncedLine(last.time, mergedLineText, last.words)
                    } else {
                        SyncedLine(last.time, mergedLineText)
                    }

                    syncedLines.add(merged)
                } else {
                    // Si no hay sincronización en el archivo, es texto plano
                    plainLines.add(stripped)
                }
            }
        }

        return if (isSynced && syncedLines.isNotEmpty()) {
            val sortedSyncedLines = syncedLines.sortedBy { it.time }
            val pairedLines = pairTranslationLines(sortedSyncedLines).map { line ->

                val romanized = when {
                    MultiLangRomanizer.isJapanese(line.line) -> MultiLangRomanizer.romanizeJapanese(line.line)
                    MultiLangRomanizer.isChinese(line.line) -> MultiLangRomanizer.romanizeChinese(line.line)
                    MultiLangRomanizer.isKorean(line.line) -> MultiLangRomanizer.romanizeKorean(line.line)
                    MultiLangRomanizer.isHindi(line.line) -> MultiLangRomanizer.romanizeHindi(line.line)
                    MultiLangRomanizer.isPunjabi(line.line) -> MultiLangRomanizer.romanizePunjabi(line.line)
                    MultiLangRomanizer.isCyrillic(line.line) -> MultiLangRomanizer.romanizeCyrillic(line.line)
                    else -> null
                }?.capitalizeFirstLetter()?.trim()

                val origTrans = line.translation?.trim()

                line.copy(romanization = romanized)
            }
            val plainVersion = pairedLines.map { line ->
                buildString {
                    append(line.line)
                    if (!line.romanization.isNullOrEmpty()) append("\n").append(line.romanization)
                    if (!line.translation.isNullOrEmpty()) append("\n").append(line.translation)
                }
            }
            Lyrics(synced = pairedLines, plain = plainVersion)
        } else {
            val processedPlain = plainLines.map { line ->
                val romanized = when {
                    MultiLangRomanizer.isJapanese(line) -> MultiLangRomanizer.romanizeJapanese(line)
                    MultiLangRomanizer.isChinese(line) -> MultiLangRomanizer.romanizeChinese(line)
                    MultiLangRomanizer.isKorean(line) -> MultiLangRomanizer.romanizeKorean(line)
                    MultiLangRomanizer.isHindi(line) -> MultiLangRomanizer.romanizeHindi(line)
                    MultiLangRomanizer.isPunjabi(line) -> MultiLangRomanizer.romanizePunjabi(line)
                    MultiLangRomanizer.isCyrillic(line) -> MultiLangRomanizer.romanizeCyrillic(line)
                    else -> null
                }?.capitalizeFirstLetter()?.trim()

                if (!romanized.isNullOrEmpty()) "$line\n$romanized" else line
            }
            Lyrics(plain = processedPlain)
        }
    }

    /**
     * Pairs consecutive synced lines that share the same timestamp.
     * The second line is treated as a translation of the first.
     * Only pairs one translation per original — a third line at the same timestamp stays separate.
     */
    internal fun pairTranslationLines(lines: List<SyncedLine>): List<SyncedLine> {
        if (lines.size < 2) return lines
        val result = mutableListOf<SyncedLine>()
        var i = 0
        while (i < lines.size) {
            val current = lines[i]
            val next = lines.getOrNull(i + 1)
            if (next != null && next.time == current.time && current.translation == null && current.line.isNotBlank() && next.line.isNotBlank()) {
                val translationParts = mutableListOf(next.line)
                var consumed = 2
                while (true) {
                    val trailing = lines.getOrNull(i + consumed) ?: break
                    if (trailing.time != current.time || !isTranslationCreditLine(trailing.line)) break
                    translationParts.add(trailing.line)
                    consumed++
                }
                result.add(current.copy(translation = translationParts.joinToString("\n")))
                i += consumed
            } else {
                result.add(current)
                i++
            }
        }
        return result
    }

    internal fun stripLrcTimestamps(value: String): String {
        if (value.isEmpty()) return value
        val withoutTags = LRC_TIMESTAMP_TAG_REGEX.replace(value, "")
        return withoutTags.trimStart()
    }

    internal fun isTranslationCreditLine(line: String): Boolean {
        val normalized = stripLrcTimestamps(line).trim()
        return normalized.isNotEmpty() && TRANSLATION_CREDIT_REGEX.matches(normalized)
    }

    /**
     * Converts synced lyrics to LRC format string.
     * Each line is formatted as [mm:ss.xx]text
     * @param syncedLines The list of synced lines to convert.
     * @return A string in LRC format.
     */
    fun syncedToLrcString(syncedLines: List<SyncedLine>): String {
        return syncedLines.sortedBy { it.time }.flatMap { line ->
            val totalMs = line.time
            val minutes = totalMs / 60000
            val seconds = (totalMs % 60000) / 1000
            val hundredths = (totalMs % 1000) / 10
            val timestamp = "[%02d:%02d.%02d]".format(minutes, seconds, hundredths)
            buildList {
                add("$timestamp${line.line}")
                if (!line.translation.isNullOrBlank()) {
                    line.translation
                        .lines()
                        .filter { it.isNotBlank() }
                        .forEach { translationLine ->
                            add("$timestamp$translationLine")
                        }
                }
            }
        }.joinToString("\n")
    }

    /**
     * Converts plain lyrics (list of lines) to a plain text string.
     * @param plainLines The list of plain text lines.
     * @return A string with each line separated by newline.
     */
    fun plainToString(plainLines: List<String>): String {
        return plainLines.joinToString("\n")
    }

    /**
     * Converts Lyrics object to LRC or plain text format based on available data.
     * Prefers synced lyrics if available.
     * @param lyrics The Lyrics object to convert.
     * @param preferSynced Whether to prefer synced lyrics over plain. Default true.
     * @return A string representation of the lyrics.
     */
    fun toLrcString(lyrics: Lyrics, preferSynced: Boolean = true): String {
        return if (preferSynced && !lyrics.synced.isNullOrEmpty()) {
            syncedToLrcString(lyrics.synced)
        } else if (!lyrics.plain.isNullOrEmpty()) {
            plainToString(lyrics.plain)
        } else if (!lyrics.synced.isNullOrEmpty()) {
            syncedToLrcString(lyrics.synced)
        } else {
            ""
        }
    }
}

private fun sanitizeLrcLine(rawLine: String): String {
    if (rawLine.isEmpty()) return rawLine

    val withoutTerminators = rawLine
        .trimEnd('\r', '\n')
        .filterNot { char ->
            Character.getType(char).toByte() == Character.FORMAT ||
                (Character.isISOControl(char) && char != '\t')
        }
        .trimEnd('\uFEFF')

    val trimmedPrefix = withoutTerminators.trimStart { it.isWhitespace() }
    val firstBracket = trimmedPrefix.indexOf('[')
    return if (firstBracket > 0) {
        trimmedPrefix.substring(firstBracket)
    } else {
        trimmedPrefix
    }
}

private fun stripFormatCharacters(value: String): String {
    val cleaned = value.filterNot { char ->
        Character.getType(char).toByte() == Character.FORMAT ||
            (Character.isISOControl(char) && char != '\t')
    }

    return when (cleaned) {
        "\"", "'" -> ""
        else -> cleaned
    }
}

@Composable
fun ProviderText(
    providerText: String,
    uri: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    accentColor: Color? = null
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = accentColor ?: MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val annotatedString = buildAnnotatedString {
        withStyle(style = SpanStyle(color = textColor)) {
            append(providerText)
        }
        pushStringAnnotation(tag = "URL", annotation = uri)
        withStyle(style = SpanStyle(color = linkColor)) {
            append(" LRCLIB")
        }
        pop()
    }

    val baseStyle = MaterialTheme.typography.bodySmall
    val finalStyle = textAlign?.let { baseStyle.copy(textAlign = it) } ?: baseStyle
    
    ClickableText(
        text = annotatedString,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    uriHandler.openUri(annotation.item)
                }
        },
        style = finalStyle,
        modifier = modifier
    )
}

/**
 * Un composable que muestra una línea de burbujas animadas que se transforman
 * en notas musicales cuando suben y vuelven a ser círculos cuando bajan.
 *
 * @param positionFlow Un flujo que emite la posición de reproducción actual.
 * @param time El tiempo de inicio para que estas burbujas sean visibles.
 * @param color El color base para las burbujas y las notas.
 * @param nextTime El tiempo final para que estas burbujas sean visibles.
 * @param modifier El modificador a aplicar a este layout.
 */
@Composable
fun BubblesLine(
    positionFlow: Flow<Long>,
    time: Int,
    color: Color,
    nextTime: Int,
    modifier: Modifier = Modifier,
) {
    val position by positionFlow.collectAsStateWithLifecycle(initialValue = 0L)
    val isCurrent = position in time until nextTime
    val transition = rememberInfiniteTransition(label = "bubbles_transition")

    // Animación ralentizada para apreciar mejor el efecto.
    val animatedValue by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "bubble_animation_progress"
    )

    var show by remember { mutableStateOf(false) }
    LaunchedEffect(isCurrent) {
        show = isCurrent
    }

    if (show) {
        val density = LocalDensity.current
        // Círculos más pequeños para acentuar la animación de escala.
        val bubbleRadius = remember(density) { with(density) { 4.dp.toPx() } }

        val (morphableCircle, morphableNote) = remember(bubbleRadius) {
            val circleNodes = createCirclePathNodes(radius = bubbleRadius)
            val noteNodes = createVectorNotePathNodes(targetSize = bubbleRadius * 2.5f)

            makePathsCompatible(circleNodes, noteNodes)
            circleNodes to noteNodes
        }

        Canvas(modifier = modifier.size(64.dp, 48.dp)) {
            val bubbleCount = 3
            val bubbleColor = color.copy(alpha = 0.7f)

            for (i in 0 until bubbleCount) {
                val progress = (animatedValue + i * (1f / bubbleCount)) % 1f
                val yOffset = sin(progress * 2 * PI).toFloat() * 8.dp.toPx()

                val morphProgress = when {
                    progress in 0f..0.25f -> progress / 0.25f
                    progress in 0.25f..0.5f -> 1.0f - (progress - 0.25f) / 0.25f
                    else -> 0f
                }.toFloat().coerceIn(0f, 1f)

                // La animación de escalado ahora es más pronunciada.
                val scale = lerpFloat(1.0f, 1.4f, morphProgress)

                // Se calcula un desplazamiento horizontal dinámico que se activa con el morphing.
                val xOffsetCorrection = lerpFloat(0f, bubbleRadius * 1.8f, morphProgress)

                val morphedPath = lerpPath(
                    start = morphableCircle,
                    stop = morphableNote,
                    fraction = morphProgress
                ).toPath()

                // Se posiciona el contenedor de la animación en su columna.
                translate(left = (size.width / (bubbleCount + 1)) * (i + 1)) {
                    // Se aplica el desplazamiento vertical (onda) y la corrección horizontal.
                    val drawOffset = Offset(x = xOffsetCorrection, y = size.height / 2 + yOffset)

                    translate(left = drawOffset.x, top = drawOffset.y) {
                        // Se aplica la transformación de escala antes de dibujar.
                        scale(scale = scale, pivot = Offset.Zero) {
                            drawPath(
                                path = morphedPath,
                                color = bubbleColor
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Lógica de Path Morphing ---

private fun lerpPath(start: List<PathNode>, stop: List<PathNode>, fraction: Float): List<PathNode> {
    return start.mapIndexed { index, startNode ->
        val stopNode = stop[index]
        when (startNode) {
            is PathNode.MoveTo -> {
                val stopMoveTo = stopNode as PathNode.MoveTo
                PathNode.MoveTo(
                    lerpFloat(startNode.x, stopMoveTo.x, fraction),
                    lerpFloat(startNode.y, stopMoveTo.y, fraction)
                )
            }
            is PathNode.CurveTo -> {
                val stopCurveTo = stopNode as PathNode.CurveTo
                PathNode.CurveTo(
                    lerpFloat(startNode.x1, stopCurveTo.x1, fraction),
                    lerpFloat(startNode.y1, stopCurveTo.y1, fraction),
                    lerpFloat(startNode.x2, stopCurveTo.x2, fraction),
                    lerpFloat(startNode.y2, stopCurveTo.y2, fraction),
                    lerpFloat(startNode.x3, stopCurveTo.x3, fraction),
                    lerpFloat(startNode.y3, stopCurveTo.y3, fraction)
                )
            }
            else -> startNode
        }
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

private fun List<PathNode>.toPath(): Path = Path().apply {
    this@toPath.forEach { node ->
        when (node) {
            is PathNode.MoveTo -> moveTo(node.x, node.y)
            is PathNode.LineTo -> lineTo(node.x, node.y)
            is PathNode.CurveTo -> cubicTo(node.x1, node.y1, node.x2, node.y2, node.x3, node.y3)
            is PathNode.Close -> close()
            else -> {}
        }
    }
}

private fun makePathsCompatible(nodes1: MutableList<PathNode>, nodes2: MutableList<PathNode>): Pair<MutableList<PathNode>, MutableList<PathNode>> {
    while (nodes1.size < nodes2.size) {
        nodes1.add(nodes1.size - 1, nodes1[nodes1.size - 2])
    }
    while (nodes2.size < nodes1.size) {
        nodes2.add(nodes2.size - 1, nodes2[nodes2.size - 2])
    }
    return nodes1 to nodes2
}

private fun createVectorNotePathNodes(targetSize: Float): MutableList<PathNode> {
    val pathData = "M239.5,1.9c-4.6,1.1 -8.7,3.6 -12.2,7.3 -6.7,6.9 -6.3,-2.5 -6.3,151.9 0,76.9 -0.3,140 -0.7,140.2 -0.5,0.3 -4.2,-0.9 -8.3,-2.5 -48.1,-19.3 -102.8,-8.3 -138.6,27.7 -35.8,36.1 -41.4,85.7 -13.6,120.7 18.6,23.4 52.8,37.4 86.2,35.3 34.8,-2.1 65.8,-16 89.5,-39.9 14.5,-14.6 24.9,-31.9 30.7,-50.6l2.3,-7.5 0.2,-133c0.2,-73.2 0.5,-133.6 0.8,-134.2 0.8,-2.4 62,28.5 84.3,42.4 22.4,14.1 34.1,30.4 37.2,51.9 2.4,16.5 -2.2,34.5 -13,50.9 -6,9.1 -7,12.1 -4.8,14.3 2.2,2.2 5.3,1.2 13.8,-4.5 26.4,-17.9 45.6,-48 50,-78.2 1.9,-12.9 0.8,-34.3 -2.4,-46.1 -8.7,-31.7 -30.4,-58 -64.1,-77.8 -64.3,-37.9 -116,-67.3 -119.6,-68.1 -5,-1.2 -7.1,-1.2 -11.4,-0.2z"
    val parser = PathParser().parsePathString(pathData)

    val groupScale = 0.253f
    val bounds = Path().apply { parser.toPath(this) }.getBounds()
    val maxDimension = max(bounds.width, bounds.height)
    val scale = if (maxDimension > 0f) targetSize / (maxDimension * groupScale) else 1f

    val matrix = Matrix()
    matrix.translate(x = -bounds.left, y = -bounds.top)
    matrix.scale(x = groupScale * scale, y = groupScale * scale)
    val finalWidth = bounds.width * groupScale * scale
    val finalHeight = bounds.height * groupScale * scale

    // Se centra el path en su origen (0,0) sin correcciones estáticas.
    matrix.translate(x = -finalWidth / 2f, y = -finalHeight / 2f)

    return parser.toNodes().toAbsolute().transform(matrix).toCurvesOnly()
}

private fun createCirclePathNodes(radius: Float): MutableList<PathNode> {
    val kappa = 0.552284749831f
    val rk = radius * kappa
    return mutableListOf(
        PathNode.MoveTo(0f, -radius),
        PathNode.CurveTo(rk, -radius, radius, -rk, radius, 0f),
        PathNode.CurveTo(radius, rk, rk, radius, 0f, radius),
        PathNode.CurveTo(-rk, radius, -radius, rk, -radius, 0f),
        PathNode.CurveTo(-radius, -rk, -rk, -radius, 0f, -radius),
        PathNode.Close
    )
}

// --- Funciones de Extensión para PathNode ---

private fun List<PathNode>.toAbsolute(): MutableList<PathNode> {
    val absoluteNodes = mutableListOf<PathNode>()
    var currentX = 0f
    var currentY = 0f
    this.forEach { node ->
        when (node) {
            is PathNode.MoveTo -> { currentX = node.x; currentY = node.y; absoluteNodes.add(node) }
            is PathNode.RelativeMoveTo -> { currentX += node.dx; currentY += node.dy; absoluteNodes.add(PathNode.MoveTo(currentX, currentY)) }
            is PathNode.LineTo -> { currentX = node.x; currentY = node.y; absoluteNodes.add(node) }
            is PathNode.RelativeLineTo -> { currentX += node.dx; currentY += node.dy; absoluteNodes.add(PathNode.LineTo(currentX, currentY)) }
            is PathNode.CurveTo -> { currentX = node.x3; currentY = node.y3; absoluteNodes.add(node) }
            is PathNode.RelativeCurveTo -> {
                absoluteNodes.add(PathNode.CurveTo(currentX + node.dx1, currentY + node.dy1, currentX + node.dx2, currentY + node.dy2, currentX + node.dx3, currentY + node.dy3))
                currentX += node.dx3; currentY += node.dy3
            }
            is PathNode.Close -> absoluteNodes.add(node)
            else -> {}
        }
    }
    return absoluteNodes
}

private fun MutableList<PathNode>.toCurvesOnly(): MutableList<PathNode> {
    val curveNodes = mutableListOf<PathNode>()
    var lastX = 0f
    var lastY = 0f

    this.forEach { node ->
        when(node) {
            is PathNode.MoveTo -> { curveNodes.add(node); lastX = node.x; lastY = node.y }
            is PathNode.LineTo -> { curveNodes.add(PathNode.CurveTo(lastX, lastY, node.x, node.y, node.x, node.y)); lastX = node.x; lastY = node.y }
            is PathNode.CurveTo -> { curveNodes.add(node); lastX = node.x3; lastY = node.y3 }
            is PathNode.Close -> curveNodes.add(node)
            else -> {}
        }
    }
    return curveNodes
}

private fun List<PathNode>.transform(matrix: Matrix): MutableList<PathNode> {
    return this.map { node ->
        when (node) {
            is PathNode.MoveTo -> PathNode.MoveTo(matrix.map(Offset(node.x, node.y)).x, matrix.map(Offset(node.x, node.y)).y)
            is PathNode.LineTo -> PathNode.LineTo(matrix.map(Offset(node.x, node.y)).x, matrix.map(Offset(node.x, node.y)).y)
            is PathNode.CurveTo -> {
                val p1 = matrix.map(Offset(node.x1, node.y1))
                val p2 = matrix.map(Offset(node.x2, node.y2))
                val p3 = matrix.map(Offset(node.x3, node.y3))
                PathNode.CurveTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
            }
            else -> node
        }
    }.toMutableList()
}
