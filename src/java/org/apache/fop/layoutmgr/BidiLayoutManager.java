/*
 * Copyright 1999-2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* $Id$ */

package org.apache.fop.layoutmgr;

import java.util.ArrayList;
import java.util.List;

import org.apache.fop.area.inline.InlineArea;


/**
 * If this bidi has a different writing mode direction
 * ltr or rtl than its parent writing mode then this
 * reverses the inline areas (at the character level).
 */
public class BidiLayoutManager extends LeafNodeLayoutManager {

    private List children;

    public BidiLayoutManager(InlineStackingLayoutManager cLM) {
        children = new ArrayList();
/*
        for (int count = cLM.size() - 1; count >= 0; count--) {
            InlineArea ia = cLM.get(count);
            if (ia instanceof Word) {
                // reverse word
                Word word = (Word) ia;
                StringBuffer sb = new StringBuffer(word.getWord());
                word.setWord(sb.reverse().toString());
            }
            children.add(ia);
        }
*/
    }

    public int size() {
        return children.size();
    }

    public InlineArea get(int index) {
        return (InlineArea) children.get(index);
    }

}
