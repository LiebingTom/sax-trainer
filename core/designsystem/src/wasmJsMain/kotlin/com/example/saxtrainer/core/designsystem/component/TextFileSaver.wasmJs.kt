package com.example.saxtrainer.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** An ordinary browser download: the file lands wherever the browser puts downloads. */
@Composable
actual fun rememberTextFileSaver(): TextFileSaver = remember {
    TextFileSaver { fileName, mimeType, text -> downloadText(fileName, mimeType, text) }
}

@JsFun(
    """(fileName, mimeType, text) => {
        const url = URL.createObjectURL(new Blob([text], { type: mimeType }));
        const link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 0);
    }""",
)
private external fun downloadText(fileName: String, mimeType: String, text: String)
