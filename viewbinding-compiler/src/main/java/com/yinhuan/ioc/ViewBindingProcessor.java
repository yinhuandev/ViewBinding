package com.yinhuan.ioc;

import com.google.auto.service.AutoService;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * Created by yinhuan on 2017/3/12.
 */
@AutoService(Processor.class)
public class ViewBindingProcessor extends AbstractProcessor {

    private Filer mFilerUtil;

    private Elements mElementsUtil;

    private Messager mMessager;

    private Map<String, ProxyInfo> mProxyMap = new HashMap<String, ProxyInfo>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);

        //跟文件相关的辅助类，生成JavaSourceCode
        mFilerUtil = processingEnvironment.getFiler();

        //跟元素相关的辅助类，帮助我们去获取一些元素相关的信息
        mElementsUtil = processingEnvironment.getElementUtils();

        //跟日志相关的辅助类
        mMessager = processingEnvironment.getMessager();
    }


    /**
     * 返回支持的注解类型
     * @return
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotationTypes = new LinkedHashSet<String>();
        annotationTypes.add(BindView.class.getCanonicalName());
        return annotationTypes;
    }


    /**
     * 返回支持的源码版本
     * @return
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }


    /**
     * 1.收集信息
     * 2.生成代理类（本文把编译时生成的类叫代理类）
     *
     * @param set
     * @param roundEnvironment
     * @return
     */
    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        {
            mMessager.printMessage(Diagnostic.Kind.NOTE, "process...");
            mProxyMap.clear();

            Set<? extends Element> elesWithBind = roundEnvironment.getElementsAnnotatedWith(BindView.class);
            for (Element element : elesWithBind) {
                checkAnnotationValid(element, BindView.class);

                VariableElement variableElement = (VariableElement) element;
                //class type
                TypeElement classElement = (TypeElement) variableElement.getEnclosingElement();
                //full class name
                String fqClassName = classElement.getQualifiedName().toString();

                ProxyInfo proxyInfo = mProxyMap.get(fqClassName);
                if (proxyInfo == null) {
                    proxyInfo = new ProxyInfo(mElementsUtil, classElement);
                    mProxyMap.put(fqClassName, proxyInfo);
                }

                BindView bindAnnotation = variableElement.getAnnotation(BindView.class);
                int id = bindAnnotation.value();
                proxyInfo.injectVariables.put(id, variableElement);
            }

            for (String key : mProxyMap.keySet()) {
                ProxyInfo proxyInfo = mProxyMap.get(key);
                try {
                    JavaFileObject jfo = processingEnv.getFiler().createSourceFile(
                            proxyInfo.getProxyClassFullName(),
                            proxyInfo.getTypeElement());
                    Writer writer = jfo.openWriter();
                    writer.write(proxyInfo.generateJavaCode());
                    writer.flush();
                    writer.close();
                } catch (IOException e) {
                    error(proxyInfo.getTypeElement(),
                            "Unable to write injector for type %s: %s",
                            proxyInfo.getTypeElement(), e.getMessage());
                }

            }
            return true;
        }

    }

    private boolean checkAnnotationValid(Element annotatedElement, Class clazz)
    {
        if (annotatedElement.getKind() != ElementKind.FIELD)
        {
            error(annotatedElement, "%s must be declared on field.", clazz.getSimpleName());
            return false;
        }
        if (ClassValidator.isPrivate(annotatedElement))
        {
            error(annotatedElement, "%s() must can not be private.", annotatedElement.getSimpleName());
            return false;
        }

        return true;
    }

    private void error(Element element, String message, Object... args)
    {
        if (args.length > 0)
        {
            message = String.format(message, args);
        }
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message, element);
    }
}
