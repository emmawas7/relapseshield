package com.example.relapseshield;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.view.WindowManager;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ScreenInsets {

    // Keeps the header behind the status bar, the bottom view clear of the navigation bar,
    // and the whole screen above the keyboard, so the box being typed in stays visible
    public static void apply(View root, View header, View bottom) {
        Context c = root.getContext();
        while (c instanceof ContextWrapper && !(c instanceof Activity)) {
            c = ((ContextWrapper) c).getBaseContext();
        }
        if (c instanceof Activity) {
            ((Activity) c).getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        int headerTop = header.getPaddingTop();
        int bottomPad = bottom.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int keyboard = Math.max(0, ime.bottom - bars.bottom);

            v.setPadding(bars.left, 0, bars.right, keyboard);
            header.setPadding(header.getPaddingLeft(), headerTop + bars.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            bottom.setPadding(bottom.getPaddingLeft(), bottom.getPaddingTop(),
                    bottom.getPaddingRight(), bottomPad + bars.bottom);
            return insets;
        });
    }
}