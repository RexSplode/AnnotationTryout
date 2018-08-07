package com.zhaldak.sinletonprocessor;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import com.zhaldak.singleton_annotation.Singleton;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

/**
 * Created by Lileia on 29.07.2018.
 */

@AutoService(Processor.class)
public class SingletonProcessor extends AbstractProcessor {

    public static final String METHOD_SUFFIX = "Instance";
    public static final String FIELD_SUFFIX = "_INSTANCE";
    private Filer filer;
    private Messager messager;
    private Elements elements;
    private Map<String, String> singletonAnnotatedClasses;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
        elements = processingEnv.getElementUtils();
        singletonAnnotatedClasses = new HashMap<>();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return ImmutableSet.of(Singleton.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Singleton.class)) {
            String name = element.getSimpleName().toString();

            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Singleton can be applied only to classes: " + name);
                return true;
            }

            TypeElement typeElement = (TypeElement) element;

            if (typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Singleton cannot be applied to abstract class: " + name);
                return true;

            }

            if (!hasPublicConstructorNoArgs(typeElement)) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Empty public constructor should be provided: " + name);
                return true;
            }

            singletonAnnotatedClasses.put(typeElement.getSimpleName().toString(),
                    elements.getPackageOf(typeElement).getQualifiedName().toString());

        }
        TypeSpec.Builder singletonsClass =
                TypeSpec.classBuilder("Singletons")
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        if (singletonAnnotatedClasses.size() != 0) {

            for (Map.Entry<String, String> entry : singletonAnnotatedClasses.entrySet()) {
                String className = entry.getKey();
                String packageName = entry.getValue();

                ClassName fullName = ClassName.get(packageName, className);
                String lowercaseClassName = className.substring(0, 1).toLowerCase() + className.substring(1);


                FieldSpec instanceField = FieldSpec.builder(fullName, lowercaseClassName + FIELD_SUFFIX)
                        .addModifiers(Modifier.PRIVATE)
                        .build();


                MethodSpec instanceMethod = MethodSpec
                        .methodBuilder(className + METHOD_SUFFIX)
                        .addModifiers(Modifier.FINAL, Modifier.PUBLIC)
                        .returns(fullName)
                        .beginControlFlow("if ($N == null)", instanceField)
                        .addStatement("$N = new $T()", instanceField, fullName)
                        .endControlFlow()
                        .addStatement("return $N", instanceField)
                        .build();

                singletonsClass.addField(instanceField).addMethod(instanceMethod);

            }
            singletonAnnotatedClasses.clear();

            try {
                JavaFile.builder("com.zhaldak.annotationtryout",
                        singletonsClass.build()).build().writeTo(filer);
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage());
                return false;
            }

            return true;

        }
        return true;
    }

    private boolean hasPublicConstructorNoArgs(TypeElement typeElement) {
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement constructorElement = (ExecutableElement) enclosed;

                if (constructorElement.getParameters().size() == 0
                        && constructorElement.getModifiers().contains(Modifier.PUBLIC)) {
                    return true;
                }
            }
        }
        return false;
    }
}
