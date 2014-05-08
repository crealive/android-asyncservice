package com.joanzap.minim.processors;

import com.joanzap.minim.api.annotation.MinimService;
import com.joanzap.minim.api.internal.Minim;
import com.joanzap.minim.processors.utils.Logger;
import com.joanzap.minim.processors.utils.Utils;
import com.squareup.javawriter.JavaWriter;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import static java.util.EnumSet.of;
import static javax.lang.model.element.Modifier.*;

@SupportedAnnotationTypes({"com.joanzap.minim.api.annotation.MinimService"})
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class MinimServiceAP extends AbstractProcessor {

    public static final String GENERATED_CLASS_SUFFIX = "Impl";

    @Override
    public boolean process(Set<? extends TypeElement> typeElements, RoundEnvironment roundEnvironment) {
        try {

            // Initialize a logger
            Logger logger = new Logger(processingEnv.getMessager());

            // Retrieve @MinimService annotated elements
            Set<? extends Element> minimServices = roundEnvironment.getElementsAnnotatedWith(MinimService.class);

            // Loop through elements
            for (Element minimServiceElement : minimServices) {
                logger.note("Processing @MinimService on " + minimServiceElement);

                // Get name and package
                String elementName = minimServiceElement.getSimpleName().toString();
                String elementPackage = Utils.getElementPackageName(minimServiceElement);

                // Create the output file
                String newElementName = elementName + GENERATED_CLASS_SUFFIX;
                String targetFile = ((TypeElement) minimServiceElement).getQualifiedName() + GENERATED_CLASS_SUFFIX;
                JavaFileObject classFile = processingEnv.getFiler().createSourceFile(targetFile);
                logger.note(classFile.toUri().toString());
                Writer out = classFile.openWriter();
                JavaWriter writer = new JavaWriter(out);

                // Start writing the file
                JavaWriter classWriter = writer.emitPackage(elementPackage)
                        .emitImports(Minim.class)
                        .emitImports(
                                minimServiceElement.toString(),
                                "android.content.Context")
                        .emitEmptyLine()
                        .beginType(newElementName, "class", of(PUBLIC, FINAL), minimServiceElement.toString());

                // Create the emitter field
                classWriter
                        .emitEmptyLine()
                        .emitField("Object", "emitter", of(PRIVATE, FINAL));

                // Generate a public constructor
                classWriter
                        .emitEmptyLine()
                        .beginConstructor(of(PUBLIC), "Object", "emitter")
                        .emitStatement("this.emitter = emitter")
                        .endConstructor();

                // Manage each method
                for (Element element : minimServiceElement.getEnclosedElements())
                    if (Utils.isPublicMethod(element))
                        createDelegateMethod(classWriter, (ExecutableElement) element);

                classWriter.endType();

                out.flush();
                out.close();
            }
            return true;
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void createDelegateMethod(JavaWriter classWriter, ExecutableElement method) throws IOException {

        // Start the mimic method
        classWriter.emitEmptyLine()
                .beginMethod(
                        method.getReturnType().toString(),
                        method.getSimpleName().toString(),
                        method.getModifiers(),
                        Utils.formatParameters(method, true), null)

                        // Delegate the call to the user method
                .emitStatement("Minim.dispatch(super.%s(%s))",
                        method.getSimpleName(),
                        Utils.formatParametersForCall(method))

                .emitStatement("return null")
                .endMethod();

    }
}