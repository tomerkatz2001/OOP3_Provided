package OOP.Solution;
import OOP.Provided.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

//import OOP.Provided.IllegalBindException;


public class Injector {

    private HashMap<Class<?>,Class<?>> classToClassBindings;
    private HashMap<Class<?>,Object> classToInstanceBindings;
    private HashMap<Class<?>,Supplier<?>> classToSupplierBindings;
    private HashMap<String,Class<?>> stringToClassBindings;

    public Injector(){
        this.classToClassBindings = new HashMap<>();
        this.classToInstanceBindings = new HashMap<>();
        this.classToSupplierBindings = new HashMap<>();
        this.stringToClassBindings = new HashMap<>();
    }

    public void bind(Class clazz1, Class clazz2) throws IllegalBindException{
        /*
        boolean f = false;
        Class<?> parent = clazz2.getSuperclass();
        while (parent != null){
            if(parent == clazz1.getClass()){
                f = true;
                break;
            }
            parent=parent.getSuperclass();
        }
        if(!f){
            throw new IllegalBindException();
        }
        */
        //this.classToClassBindings.remove(clazz);
        //this.classToSupplierBindings.remove(clazz);
        if(clazz1.isAssignableFrom(clazz2)){// clazz2 inherit clazz1
            this.classToInstanceBindings.remove(clazz1);
            this.classToSupplierBindings.remove(clazz1);
            classToClassBindings.put(clazz1,clazz2);
        } else{
            throw new IllegalBindException();
        }
    }

    public void bindToInstance(Class clazz, Object obj) throws IllegalBindException{

        // handle bind(A,A) --> need to remove A bindings or so
        if(!clazz.isAssignableFrom(obj.getClass())){// obj inherit from clazz
            throw new IllegalBindException();
        }
        this.classToClassBindings.remove(clazz);
        this.classToSupplierBindings.remove(clazz);
        this.classToInstanceBindings.put(clazz,obj);
    }

    public void bindToSupplier(Class clazz, Supplier sup){//check if the supplier is valid
        this.classToClassBindings.remove(clazz);
        this.classToInstanceBindings.remove(clazz);
        this.classToSupplierBindings.put(clazz,sup);
    }


    public void bindByName(String s, Class clazz){
        this.stringToClassBindings.put(s,clazz);
    }

    public Object construct(Class clazz) throws MultipleInjectConstructorsException, NoConstructorFoundException, NoSuitableProviderFoundException, MultipleProvidersException, MultipleAnnotationOnParameterException{
        if(this.classToClassBindings.containsKey(clazz)){
            return construct(this.classToClassBindings.get(clazz));
        }
        if(this.classToInstanceBindings.containsKey(clazz)){
            return this.classToInstanceBindings.get(clazz);
        }
        if(this.classToSupplierBindings.containsKey(clazz)){
            return (this.classToSupplierBindings.get(clazz)).get();
        }

        Constructor<?> constructors[] = clazz.getDeclaredConstructors();//changed
        Object c = null;

        List<Constructor> filtered_cs =  Arrays.stream(constructors).filter(m -> m.isAnnotationPresent(Inject.class)).collect(Collectors.toList());
        if(filtered_cs.size() > 1){ //more then one constructor is annotated  with "inject"
            throw new MultipleInjectConstructorsException();
        }
        if(filtered_cs.size() < 1){//no constructor is annotated with "inject"
            List<Constructor> cons_with_no_inject=Arrays.stream(constructors).filter(m -> !m.isAnnotationPresent(Inject.class)).collect(Collectors.toList());
            for (Constructor cwni : cons_with_no_inject){// TODO : check if can be private constructor
                if(cwni.getParameterCount() == 0){
                    try {
                        cwni.setAccessible(true);
                        c = cwni.newInstance();
                        break;
                    } catch (Exception e) {} // should not get here
                }
            }
            if(c == null){// no zero params constructor was found
                throw new NoConstructorFoundException();
            }
        }

//        if(filtered_cs.get(0).getParameterCount() > 0){
//            throw new NoConstructorFoundException(); // is it?
//        }

        ArrayList<Object> actual_params = new ArrayList<>();
        Parameter formal_params[];
        //Constructor<?> params_cons[];





        if(c == null) {
            formal_params = filtered_cs.get(0).getParameters();
            actual_params = fillParams(formal_params);
            try {
                filtered_cs.get(0).setAccessible(true);
                c = filtered_cs.get(0).newInstance(actual_params.toArray());//TODO: can be sent ArrayList????
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }

        //#####################################end_part_one#######################################################################
        Method[] methods = clazz.getDeclaredMethods();
        List<Method> filtered_ms = Arrays.stream(methods).filter(m -> m.isAnnotationPresent(Inject.class)).collect(Collectors.toList());
        Parameter[] methods_formal_params;
        for (Method m: filtered_ms) {
            try {
                m.setAccessible(true);//maybe check if privet before
                methods_formal_params = m.getParameters();
                actual_params = fillParams(methods_formal_params);
                if(actual_params.size()==0)
                {
                    m.invoke(c,null);
                }
                else {
                    m.invoke(c,actual_params);
                }
            }
            catch (Exception e) {
                throw new NoConstructorFoundException();// should
            }
        }

        List<Field> fields = Arrays.stream(c.getClass().getDeclaredFields()).filter(f->f.getAnnotation(Inject.class)!=null).collect(Collectors.toList());
        //sArrays.stream(fields).forEach(f->f.setAccessible(true));
        for(Field f:fields)
        {
            try{
                f.setAccessible(true);
                f.set(c,construct(f.getType()));
            }
            catch(IllegalAccessException e){}
            catch (Exception e){
                throw e;
            }
        }

        return c;

    }

    private ArrayList<Object> fillParams(Parameter formal_params[]) throws MultipleInjectConstructorsException, NoConstructorFoundException, NoSuitableProviderFoundException, MultipleProvidersException, MultipleAnnotationOnParameterException{
        ArrayList<Object> actual_params = new ArrayList<>();
        Annotation[] annotations;
        Method params_method;


        for(Parameter p : formal_params) {
            if (p.getAnnotation(Named.class) != null && this.stringToClassBindings.get(p.getAnnotation(Named.class).value()) != null) {//1 TODO: maybe check if more than one named or annotation or something
                actual_params.add(construct(this.stringToClassBindings.get(p.getAnnotation(Named.class).value())));
            } else {//2
                if ((p.getAnnotations().length > 0) && p.getAnnotation(Named.class) == null) {
                    annotations = p.getAnnotations();
                    if (annotations.length > 1) {
                        throw new MultipleAnnotationOnParameterException();
                    }
                    Annotation an = annotations[0];
                    params_method = getMethodWithProvide(an, p.getType());
                    if (params_method != null) {
                        try {
                            params_method.setAccessible(true);
                            actual_params.add(params_method.invoke(this));
                        } catch (Exception e) { }
                    } else {//3
                        actual_params.add(construct(p.getType()));
                    }
                }
                else {
                    actual_params.add(construct(p.getType()));
                }
            }
        }
        return actual_params;
    }
    private Method getMethodWithProvide(Annotation an, Class<?> clazz) throws NoSuitableProviderFoundException, MultipleProvidersException {

        List<Method> methods = new ArrayList<>();
        List<Method> methods_temp;

        for(Class<?> o = this.getClass();o != Object.class ; o =o.getSuperclass()){
            methods_temp = Arrays.stream(o.getDeclaredMethods()).collect(Collectors.toList());
            methods_temp = methods_temp.stream().filter(m -> (m.getAnnotation(Provides.class)!=null)&&m.isAnnotationPresent(an.annotationType())).collect(Collectors.toList());//&&(m.getAnnotation()!=null)
            methods.addAll(methods_temp);
        }



        if(methods.size() == 0){
            return null;
        }

        methods = methods.stream().filter(m -> m.getReturnType() == clazz).collect(Collectors.toList());

        if(methods.size() == 0){
            throw new NoSuitableProviderFoundException();
        }

        if(methods.size() > 1){
            throw  new MultipleProvidersException();
        }

        return methods.get(0);

    }




}