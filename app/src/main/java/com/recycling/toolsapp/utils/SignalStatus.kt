import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.recycling.toolsapp.R

enum class SignalStatus(
    val value: Int?,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int
) {
    NoService(0, R.string.signal_no_service, R.drawable.ic_signal_0_no_service),
    VeryPoor(1, R.string.signal_very_poor, R.drawable.ic_signal_1_very_poor),
    Poor(2, R.string.signal_poor, R.drawable.ic_signal_2_poor),
    Normal(3, R.string.signal_normal, R.drawable.ic_signal_3_normal),
    Good(4, R.string.signal_good, R.drawable.ic_signal_4_good),
    Unknown(null, R.string.signal_unknown, R.drawable.ic_signal_unknown);

    companion object {
        fun fromValue(value: Int): SignalStatus {
            return entries.firstOrNull { it.value == value } ?: Unknown
        }
    }
}
