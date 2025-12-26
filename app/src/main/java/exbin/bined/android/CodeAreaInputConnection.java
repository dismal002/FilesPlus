/*
 * Copyright (C) ExBin Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.exbin.bined.android;

import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Binary viewer/editor component input connection.
 *
 * @author ExBin Project (https://exbin.org)
 */
@ParametersAreNonnullByDefault
public class CodeAreaInputConnection extends BaseInputConnection {

    protected final CodeAreaCore codeArea;

    public CodeAreaInputConnection(CodeAreaCore codeArea, boolean fullEditor) {
        super(codeArea, fullEditor);
        this.codeArea = codeArea;
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        android.util.Log.d("BinEd-Input", "InputConnection.commitText: \"" + text + "\"");
        for (int i = 0; i < text.length(); i++) {
            codeArea.getCommandHandler().keyTyped(text.charAt(i),
                    new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN));
        }
        return true;
    }

    @Override
    public boolean sendKeyEvent(KeyEvent keyEvent) {
        codeArea.dispatchKeyEvent(keyEvent);
        return true;
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        // Simple implementation: assume deletion of one character backwards
        if (beforeLength > 0) {
            codeArea.getCommandHandler().backSpacePressed();
            // If beforeLength > 1, we should conceptually repeat, but backSpacePressed
            // handles single char.
            // For now, handling single backspace is the primary goal.
            return true;
        }
        return super.deleteSurroundingText(beforeLength, afterLength);
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        if (text.length() > 0) {
            return commitText(text, newCursorPosition);
        }
        return super.setComposingText(text, newCursorPosition);
    }

    @Override
    public boolean finishComposingText() {
        return super.finishComposingText();
    }
}
