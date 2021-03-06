package com.example.kk.dididache.control.adapter

import android.animation.Animator
import android.content.Context
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.widget.Button
import com.example.kk.dididache.*
import com.example.kk.dididache.model.DataKeeper
import com.example.kk.dididache.model.Event.ExceptionEvent
import com.example.kk.dididache.ui.MainActivity
import com.example.kk.dididache.widget.SelectTime
import com.orhanobut.logger.Logger
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import org.greenrobot.eventbus.Subscribe
import org.jetbrains.anko.find
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Created by 小吉哥哥 on 2017/8/19.
 */
class SelectTimeManager(var ctx: Context, val initFun: SelectTimeManager.() -> Unit = {}) {

    private val selectTime: SelectTime = (ctx as MainActivity).selectTime
    private val scrimForSelectTime: View = (ctx as MainActivity).scrimForSelectTime
    private val cancelButton: View = (ctx as MainActivity).timeCancelButton
    private val selectButton: View = (ctx as MainActivity).timeSelectButton
    private val timeCardView = (ctx as MainActivity).timeCardView
    var isShowing = false
        get() = selectTime.visibility == View.VISIBLE
    var isNow = true//是不是当前时间
    var timeMode = 0//-1过去，0当前，1未来
        get() {
            val now = Calendar.getInstance().getTimeNow()
            if (isNow) return 0
            return timeSelected.compareTo(now)
        }
    var timeSelected = Calendar.getInstance()
        get() {
            if (isNow) {
                val now = Calendar.getInstance().getTimeNow()
                DataKeeper.getInstance().time = now.clone() as Calendar
                return now
            }
            DataKeeper.getInstance().time = field.clone() as Calendar
            return field.clone() as Calendar
        }
    var _select: (Calendar) -> Unit = {}

    init {
        selectTime.setIndexAfterNew(timeSelected)
        scrimForSelectTime.onClick { dismiss() }
        cancelButton.onClick { dismiss() }
        selectButton.onClick {
            if (isTimeOutOfBound(selectTime.selectedTime, 3, TimeUnit.HOURS)) {
                showToast("只能选择未来三小时内时间")
                return@onClick
            }
            isNow = false//按下选择即不是当前时间
            timeSelected = selectTime.selectedTime
            _select(timeSelected)
            timeCardView.timeButton.text = timeSelected.toStr("yyyy-MM-dd HH:mm")
            dismiss()
        }
        initFun()
    }


    fun show() {
        animate(true)
    }

    fun dismiss() {
        animate(false)
    }

    fun animate(isShow: Boolean) {
        if (isShow) {
            timeCardView.animate()
                    .translationY(-400F)
                    .setDuration(300)
                    .setInterpolator(AccelerateInterpolator())
                    .setListener(object : Animator.AnimatorListener {
                        override fun onAnimationRepeat(p0: Animator?) {

                        }

                        override fun onAnimationEnd(p0: Animator?) {
                            scrimForSelectTime.visibility = View.VISIBLE
                            selectTime.visibility = View.VISIBLE
                            cancelButton.visibility = View.VISIBLE
                            selectButton.visibility = View.VISIBLE
                            cancelButton.translationX = -400F
                            cancelButton.animate()
                                    .translationX(0F)
                                    .setInterpolator(MyOverShootInterpolator())
                                    .setDuration(300)
                                    .start()

                            selectButton.translationX = 400F
                            selectButton.animate()
                                    .translationX(0F)
                                    .setInterpolator(MyOverShootInterpolator())
                                    .setDuration(300)
                                    .start()

                            scrimForSelectTime.alpha = 0F
                            scrimForSelectTime.animate()
                                    .alpha(1F)
                                    .setDuration(300)
                                    .setInterpolator(AccelerateDecelerateInterpolator())
                                    .start()
                            selectTime.scaleY = 0.2F
                            selectTime.animate()
                                    .scaleY(1F)
                                    .setDuration(200)
                                    .setInterpolator(AccelerateDecelerateInterpolator())
                                    .setListener(object : Animator.AnimatorListener {
                                        override fun onAnimationRepeat(p0: Animator?) {

                                        }

                                        override fun onAnimationEnd(p0: Animator?) {
                                            selectTime.smoothToGoal(timeSelected)
                                        }

                                        override fun onAnimationCancel(p0: Animator?) {

                                        }

                                        override fun onAnimationStart(p0: Animator?) {
                                        }
                                    })
                                    .start()
                        }

                        override fun onAnimationCancel(p0: Animator?) {

                        }

                        override fun onAnimationStart(p0: Animator?) {

                        }
                    })
                    .start()

        } else {
            cancelButton.animate()
                    .translationX(-400F)
                    .setDuration(100)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()

            selectButton.animate()
                    .translationX(400F)
                    .setDuration(100)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            scrimForSelectTime.animate()
                    .alpha(0F)
                    .setDuration(100)
                    .start()
            selectTime.animate()
                    .scaleY(0F)
                    .setDuration(100)
                    .setListener(object : Animator.AnimatorListener {
                        override fun onAnimationRepeat(p0: Animator?) {

                        }

                        override fun onAnimationEnd(p0: Animator?) {
                            val now = Calendar.getInstance()
                            now.add(Calendar.MONTH, 1)
                            selectTime.setIndexAfterNew(now)
                            //buttons.map { it.visibility = View.VISIBLE }
                            scrimForSelectTime.visibility = View.GONE
                            selectTime.visibility = View.GONE
                            cancelButton.visibility = View.GONE
                            selectButton.visibility = View.GONE
                            timeCardView.animate()
                                    .translationY(0F)
                                    .setDuration(300)
                                    .setInterpolator(AccelerateDecelerateInterpolator())
                                    .setListener(object : Animator.AnimatorListener {
                                        override fun onAnimationRepeat(p0: Animator?) {

                                        }

                                        override fun onAnimationEnd(p0: Animator?) {

                                        }

                                        override fun onAnimationCancel(p0: Animator?) {

                                        }

                                        override fun onAnimationStart(p0: Animator?) {

                                        }
                                    })
                                    .start()
                        }

                        override fun onAnimationCancel(p0: Animator?) {

                        }

                        override fun onAnimationStart(p0: Animator?) {

                        }
                    })
                    .start()

        }
    }

    fun onSelect(s: (Calendar) -> Unit) {
        _select = s
    }

    fun freshTime() {
        isNow = true
        timeSelected = Calendar.getInstance()
        timeSelected.add(Calendar.MONTH, 1)
        selectTime.setIndexAfterNew(timeSelected)
        timeCardView.timeButton.text = "查 询 时 间"

    }

    //时间是否超出范围
    fun isTimeOutOfBound(time: Calendar, bound: Int, unit: TimeUnit): Boolean {
        when (unit) {
            TimeUnit.HOURS -> return time > { val now = Calendar.getInstance().getTimeNow();now.add(Calendar.HOUR_OF_DAY, bound);now }()

            TimeUnit.MINUTES -> return time > { val now = Calendar.getInstance().getTimeNow();now.add(Calendar.MINUTE, bound);now }()

            TimeUnit.DAYS -> return time > { val now = Calendar.getInstance().getTimeNow();now.add(Calendar.DATE, bound);now }()

            else -> throw IllegalArgumentException("can't found case")
        }
    }


}