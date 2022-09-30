/*
 * Copyright (C) 2014 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stratio.qa.doclet;

import jdk.javadoc.doclet.DocletEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class AnnotationsDoclet {

    public AnnotationsDoclet() throws IOException {
    }

    public static boolean start(DocletEnvironment root) throws IOException {
        TypeElement[] classes = root.getIncludedElements().toArray(new TypeElement[0]);

        for (int i = 0; i < classes.length; ++i) {
            StringBuilder sb = new StringBuilder();
            TypeElement cd = classes[i];
            printAnnotations(cd.getEnclosedElements().toArray(new ExecutableElement[0]), sb, classes[i].getSimpleName().toString());
            printAnnotations(cd.getTypeParameters().toArray(new ExecutableElement[0]), sb, classes[i].getSimpleName().toString());
        }

        return true;
    }

    static void printAnnotations(ExecutableElement[] mems, StringBuilder sb, String className) throws IOException {
        String annotation = "";
        for (int i = 0; i < mems.length; ++i) {
            AnnotationMirror[] annotations = mems[i].getAnnotationMirrors().toArray(new AnnotationMirror[0]);
            for (int j = 0; j < annotations.length; ++j) {
                annotation = annotations[j].getAnnotationType().toString();
                if ((annotation.endsWith("Given")) || (annotation.endsWith("When")) || (annotation.endsWith("Then"))) {
                    FileWriter htmlAnnotationJavadoc = new FileWriter("com/stratio/qa/specs/" + className + "-annotations.html");
                    BufferedWriter out = new BufferedWriter(htmlAnnotationJavadoc);
                    sb.append("<html>");
                    sb.append("<head>");
                    sb.append("<title>" + className + " Annotations</title>");
                    sb.append("</head>");
                    sb.append("<body>");
                    sb.append("<dl>");
                    sb.append("<dt><b>Regex</b>: " + annotations[j].getElementValues().values() + "</dt>");
                    sb.append("<dd><b>Step</b>: " + annotations[j].getElementValues().values().toString().replace("\\", "") + "</dd>");
                    sb.append("<dd><b>Method</b>: <a href=\"./" + className + ".html#" + ((ExecutableElement) mems[i]).getSimpleName() + "-" + ((ExecutableElement) mems[i]).toString().replace("(", "").replace(")", "").replace(", ", "-") + "-\">" + ((ExecutableElement) mems[i]).getSimpleName() + ((ExecutableElement) mems[i]).toString() + "</a></dd>");
                    sb.append("</dl>");
                    sb.append("</body>");
                    sb.append("</html>");
                    out.write(sb.toString());
                    out.close();
                }
            }
        }
    }
}
