package org.zhongmiao.interceptwave.ui

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import java.awt.GridBagConstraints
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.UIManager

/**
 * 小型表单 UI 工具，抽取常见的“标签 + 可增长文本域（带滚动）”拼装，避免重复代码。
 */
object UiFormUtil {
    /**
     * 在 parent 上按当前 gbc 行，添加：
     * - 左侧标签（使用 messageKey 国际化）
     * - 右侧带滚动的 JTextArea，并设置为自动换行、按词换行、继承默认字体
     * 调用后会将 gbc 移动到下一行（y+1），并重置为非填充，便于在同列追加按钮。
     */
    fun addLabeledScrollTextArea(
        parent: JPanel,
        gbc: GridBagConstraints,
        labelMessageKey: String,
        area: JTextArea
    ) {
        // Label at current row, left column
        parent.add(JBLabel(message(labelMessageKey)), gbc)

        // TextArea with scroll at current row, right column
        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        area.lineWrap = true
        area.wrapStyleWord = true
        area.font = UIManager.getFont("TextArea.font")
        parent.add(JBScrollPane(area), gbc)

        // Move to next row; keep at right column for possible trailing controls
        gbc.gridy = gbc.gridy + 1
        gbc.gridx = 1
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.NONE
    }
}

