package com.recycling.toolsapp.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.widget.AppCompatTextView
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import com.prolificinteractive.materialcalendarview.OnDateSelectedListener
import com.recycling.toolsapp.R

class SimpleCalendarDialogFragment : AppCompatDialogFragment(), OnDateSelectedListener {
    private var listener: OnDateSelectedListener? = null
    private var selectedDate: CalendarDay? = null
    private var textView: AppCompatTextView? = null
    var alertDialog: AlertDialog? = null

    interface OnDateSelectedListener {
        fun onDateSelected(calendarDay: CalendarDay)
    }

    fun setOnDateSelectedListener(listener: OnDateSelectedListener) {
        this.listener = listener
    }

    fun dismissDialog() {
        alertDialog?.dismiss()
    }

    fun setButtonSize() {
        val positiveButton = alertDialog?.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton?.textSize = 32f
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater: LayoutInflater? = activity?.layoutInflater
        //inflate custom layout and get views
        //pass null as parent view because will be in dialog layout
        val view: View = inflater!!.inflate(R.layout.dialog_basic, null)
        textView = view.findViewById(R.id.tv_date)
        val widget = view.findViewById<MaterialCalendarView>(R.id.calendarView)
        val today = CalendarDay.today()
        widget.state().edit().setMinimumDate(CalendarDay.from(2025, 1, 1)) // 设置最小可选日期
//                .setMaximumDate(today) // 设置最大可选日期为今天
            .commit()
        widget.setOnDateChangedListener(this)
        alertDialog =
                AlertDialog.Builder(activity).setTitle(R.string.title_date_dialogs).setView(view).setPositiveButton(android.R.string.ok) { dialog, _ ->
                    selectedDate?.let { data ->
                        listener?.onDateSelected(data)
                    }
                    dialog.dismiss()
                }.create()
        val positiveButton = alertDialog?.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton?.textSize = 64f
        return alertDialog!!
    }

    override fun onDateSelected(widget: MaterialCalendarView, calendarDay: CalendarDay, selected: Boolean) {
        selectedDate = calendarDay
        val selectedDateStr = "${calendarDay.year}年/${
            calendarDay.month.toString().padStart(2, '0')
        }月/${
            calendarDay.day.toString().padStart(2, '0')
        }日"
        textView?.text = selectedDateStr
    }
}