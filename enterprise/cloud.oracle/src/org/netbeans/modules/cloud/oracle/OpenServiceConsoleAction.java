/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.cloud.oracle;

import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;

@ActionID(
        category = "Tools",
        id = "org.netbeans.modules.cloud.oracle.OpenServiceConsoleAction"
)
@ActionRegistration(
        displayName = "#CTL_OpenServiceConsoleAction"
)
@ActionReferences(value = {
    @ActionReference(path = "Cloud/Oracle/Databases/Actions", position = 260)
})
@Messages("CTL_OpenServiceConsoleAction=Open Service Console")
public final class OpenServiceConsoleAction implements ActionListener {

    private final DatabaseItem context;

    public OpenServiceConsoleAction(DatabaseItem context) {
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        try {
            Desktop desk = Desktop.getDesktop();
            desk.browse(new URI(context.getServiceUrl()));
        } catch (URISyntaxException | IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
}
