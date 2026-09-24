package com.sandevsystems.omarchyremote.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.sandevsystems.omarchyremote.input.TypingDiff

/**
 * The "Digitar" field: every change (letters, backspace, the keyboard's corrections) is reported as
 * a [TypingDiff.Change]; [onChange] returns true when it consumed the change as a shortcut (the text
 * is then taken back out). Starts with an invisible character so backspace on an empty field still
 * reaches the PC; cleared at word ends once long, so the keyboard's corrections stay small.
 */
@SuppressLint("AppCompatCustomView")  // no AppCompat in this Compose app; a plain EditText is enough
class LiveTypingField(context: Context, private val onChange: (TypingDiff.Change) -> Boolean) : EditText(context) {
    private var shown = SENTINEL
    private var resetting = false

    init {
        setText(SENTINEL)
        setSelection(SENTINEL.length)
        setTextColor(Color.WHITE)
        setHintTextColor(Color.GRAY)
        setBackgroundColor(Color.TRANSPARENT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setPadding(24, 0, 24, 0)
        isSingleLine = true
        hint = tr("Digite: vai direto para o PC", "Type: it goes straight to the PC")
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        setOnEditorActionListener { _, _, _ ->
            onChange(TypingDiff.Change(0, "\n"))
            reset()
            true
        }
        setOnKeyListener { _, keyCode, event ->
            // Some keyboards send a hardware-style Enter instead of the action.
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                onChange(TypingDiff.Change(0, "\n"))
                reset()
                true
            } else false
        }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable) {
                if (resetting) return
                val now = s.toString()
                if (!now.startsWith(SENTINEL)) {
                    // Backspace on the empty field removed the marker: one backspace on the PC.
                    onChange(TypingDiff.Change(1, ""))
                    reset()
                    return
                }
                val change = TypingDiff.between(shown.removePrefix(SENTINEL), now.removePrefix(SENTINEL))
                shown = now
                if (change.backspaces == 0 && change.insert.isEmpty()) return
                if (onChange(change)) {
                    reset()
                    return
                }
                if (now.length > 48 && (now.endsWith(" ") || now.endsWith("\n"))) post { reset() }
            }
        })
    }

    fun showKeyboard() {
        context.getSystemService(InputMethodManager::class.java)?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun reset() {
        resetting = true
        setText(SENTINEL)
        setSelection(SENTINEL.length)
        shown = SENTINEL
        resetting = false
    }

    private companion object {
        const val SENTINEL = "​"
    }
}
