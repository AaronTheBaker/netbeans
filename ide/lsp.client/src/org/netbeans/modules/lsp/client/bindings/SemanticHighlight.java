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
package org.netbeans.modules.lsp.client.bindings;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.generator.JsonRpcData;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.modules.lsp.client.LSPBindings;
import org.netbeans.modules.lsp.client.LSPBindings.BackgroundTask;
import org.netbeans.modules.lsp.client.Utils;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
public class SemanticHighlight implements BackgroundTask {

    private final Document doc;

    public SemanticHighlight(JTextComponent c) {
        this.doc = c.getDocument();
    }

    @Override
    public void run(LSPBindings bindings, FileObject file) {
        try {
            OffsetsBag target = new OffsetsBag(doc);
            SemanticTokensParams params = new SemanticTokensParams(new TextDocumentIdentifier(Utils.toURI(file)));
            SemanticTokens tokens = bindings.getTextDocumentService().semanticTokensFull(params).get();
            List<Integer> data = tokens.getData();
            int lastLine = 0;
            int lastColumn = 0;
            int offset = 0;
            for (int i = 0; i < data.size(); i += 5) {
                int deltaLine = data.get(i).intValue();
                int deltaColumn = data.get(i + 1).intValue();
                if (deltaLine == 0) {
                    lastColumn += deltaColumn;
                    offset += deltaColumn;
                } else {
                    lastLine += deltaLine;
                    lastColumn = deltaColumn;
                    offset = Utils.getOffset(doc, new Position(lastLine, lastColumn));
                }
                if (data.get(i + 2).intValue() <= 0) {
                    continue; //XXX!
                }
                AttributeSet tokenHighlight = tokenHighlight(bindings, data.get(i + 3).intValue(), data.get(i + 4).intValue());
                target.addHighlight(offset, offset + data.get(i + 2).intValue(), tokenHighlight);
            }
            getBag(doc).setHighlights(target);
        } catch (InterruptedException | ExecutionException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private final Map<Integer, Map<Integer, AttributeSet>> tokenId2Highlight = new HashMap<>();

    private AttributeSet tokenHighlight(LSPBindings bindings, int tokenId, int modifiers) {
        return tokenId2Highlight.computeIfAbsent(tokenId, s -> new HashMap<>())
                                .computeIfAbsent(modifiers, mods -> {
            SemanticTokensLegend legend = bindings.getInitResult().getCapabilities().getSemanticTokensProvider().getLegend();
            List<String> tokenTypes = legend.getTokenTypes();
            if (tokenId >= tokenTypes.size()) {
                //invalid token id
                return EMPTY;
            }
            String tokenName = tokenTypes.get(tokenId);
            boolean isStatic = false;
            boolean isDeclaration = false;
            while (mods != 0) {
                int mod = Integer.highestOneBit(mods);
                switch (legend.getTokenModifiers().get(Integer.numberOfTrailingZeros(mod))) {
                    case "static": isStatic = true; break;
                    case "definition":
                    case "declaration": isDeclaration = true; break;
                }
                mods &= ~mod;
            }
            FontColorSettings fcs = MimeLookup.getLookup("text/textmate").lookup(FontColorSettings.class);

            if (fcs == null) {
                return EMPTY;
            }

            assert fcs != null;

            AttributeSet colors = fcs.getTokenFontColors("mod-" + tokenName + (isDeclaration ? "-declaration" : ""));
            AttributeSet statik = isStatic ? fcs.getTokenFontColors("mod-static") : null;

            if (colors != null && statik != null) {
                return AttributesUtilities.createComposite(colors, statik);
            } else if (colors != null) {
                return colors;
            } else if (statik != null) {
                return statik;
            }

            return EMPTY;
        });
    }

    private static final AttributeSet EMPTY = AttributesUtilities.createImmutable();

    private static OffsetsBag getBag(Document doc) {
        OffsetsBag bag = (OffsetsBag) doc.getProperty(SemanticHighlight.class);

        if (bag == null) {
            doc.putProperty(SemanticHighlight.class, bag = new OffsetsBag(doc));
        }

        return bag;
    }

    @MimeRegistration(mimeType="", service=HighlightsLayerFactory.class)
    public static final class HighlightsLayerFactoryImpl implements HighlightsLayerFactory {

        @Override
        public HighlightsLayer[] createLayers(Context context) {
            return new HighlightsLayer[] {
                HighlightsLayer.create(SemanticHighlight.class.getName(), ZOrder.SYNTAX_RACK.forPosition(1000), true, getBag(context.getDocument()))
            };
        }
        
    }
    
//    @JsonRpcData
//    public static class SemanticTokensParams {
//
//        private TextDocumentIdentifier textDocument;
//
//        public SemanticTokensParams() {
//        }
//
//        public SemanticTokensParams(TextDocumentIdentifier textDocument) {
//            this.textDocument = textDocument;
//        }
//
//        public TextDocumentIdentifier getTextDocument() {
//            return textDocument;
//        }
//
//        public void setTextDocument(TextDocumentIdentifier textDocument) {
//            this.textDocument = textDocument;
//        }
// 
//    }
//
//    @JsonRpcData
//    public static class SemanticTokens {
//        private List<Number> data;
//
//        public List<Number> getData() {
//            return data;
//        }
//
//        public void setData(List<Number> data) {
//            this.data = data;
//        }
//
//    }
//    public interface SemanticService {
//        @JsonRequest("textDocument/semanticTokens/full")
//        public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params);
//    }
}
