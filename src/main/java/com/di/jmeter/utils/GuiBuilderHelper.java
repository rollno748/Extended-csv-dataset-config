/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.di.jmeter.utils;

import javax.swing.*;
import java.awt.*;

public class GuiBuilderHelper {
    public static JScrollPane getTextAreaScrollPaneContainer(JTextArea textArea, int nbLines) {
        JScrollPane ret = new JScrollPane();
        textArea.setRows(nbLines);
        textArea.setColumns(20);
        ret.setViewportView(textArea);
        return ret;
    }

    public static void strechItemToComponent(JComponent component, JComponent item) {
        int iWidth = (int) item.getPreferredSize().getWidth();
        int iHeight = (int) component.getPreferredSize().getHeight();
        item.setPreferredSize(new Dimension(iWidth, iHeight));
    }

    public static JPanel getComponentWithMargin(Component component, int top, int left, int bottom, int right) {
        JPanel ret = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(top, left, bottom, right);
        ret.add(component, constraints);
        return ret;
    }
}
