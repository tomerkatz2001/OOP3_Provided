package OOP.Solution;
import OOP.Provided.*;
import OOP.Provided.IllegalBindException;
import java.util.*;
import java.util.function.Supplier;
import java.lang.reflect.*;
import java.lang.annotation.*;
import java.util.stream.Collectors;

@Target({ElementType.CONSTRUCTOR,ElementType.FIELD,ElementType.METHOD})
@Retention({RetentionPolicy.RUNTIME})
public @interface inject{}

@Target({ElementType.PARAMETER})
@Retention({RetentionPolicy.RUNTIME})
public @interface  Named {
    public String name = null;
} //TODO: check if need to imp

@Target({ElementType.METHOD})
@Retention({RetentionPolicy.RUNTIME})
public @interface Provides{}


public class Injector {

    private HashMap<Class,Class> classToClassBindings;
    private HashMap<Class,Object> classToInstanceBindings;
    private HashMap<Class,Supplier> classToSupplierBindings;
    private HashMap<String,Class> stringToClassBindings;

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
        if(clazz1.isAssignableFrom(clazz2)){// clazz2 inherit clazz1
            this.classToInstanceBindings.remove(clazz1);
            this.classToSupplierBindings.remove(clazz1);
            classToClassBindings.put(clazz1,clazz2);
        } else{
            throw new IllegalBindException();
        }
    }

    public void bindToInstance(Class clazz, Object obj) throws IllegalBindException{
        if(!clazz.isAssignableFrom(obj.getClass())){// obj inherit from clazz
            throw new IllegalBindException();
        }
        this.classToClassBindings.remove(clazz);
        this.classToSupplierBindings.remove(clazz);
        this.classToInstanceBindings.put(clazz,obj);
    }

    public void bindToSupplier(Class clazz, Supplier sup){
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

        Constructor<?> constructors[] = clazz.getConstructors();
        Object c = null;

        List<Constructor> filtered_cs =  Arrays.stream(constructors).filter(m -> m.isAnnotationPresent(inject.class)).collect(Collectors.toList());
        if(filtered_cs.size() > 1){
            throw new MultipleInjectConstructorsException();
        }
        if(filtered_cs.size() < 1){
            List<Constructor> cons_with_no_inject=Arrays.stream(constructors).filter(m -> !m.isAnnotationPresent(inject.class)).collect(Collectors.toList());
            for (Constructor cwni : cons_with_no_inject){// TODO : check if can be private constructor
                if(cwni.getParameterCount() == 0){
                    try {
                        c = cwni.newInstance();
                        break;
                    } catch (Exception e) {} // should not get here
                }
            }
            if(c == null){
                throw new NoConstructorFoundException();
            }
        }



//        if(filtered_cs.get(0).getParameterCount() > 0){
//            throw new NoConstructorFoundException(); // is it?
//        }

        ArrayList<Object> actual_params = new ArrayList<>();
        Parameter formal_params[];
        //Constructor<?> params_cons[];




        try {
            if(c == null) {
                formal_params = filtered_cs.get(0).getParameters();
                actual_params = fillParams(formal_params, c);
                c = filtered_cs.get(0).newInstance(actual_params);//TODO: can be sent ArrayList????
            }
        } catch (Exception e) {// TODO: throw the right exception
            throw new NoConstructorFoundException();
        }

        Method methods[] = clazz.getDeclaredMethods();
        List<Method> filtered_ms = Arrays.stream(methods).filter(m -> m.isAnnotationPresent(inject.class)).collect(Collectors.toList());

        for (Method m: filtered_ms) {
            try {
                formal_params = m.getParameters();
                actual_params = fillParams(formal_params, c);
                m.invoke(c,actual_params);
            } catch (Exception e) {
                throw new NoConstructorFoundException();// should
            }
        }

        Field fields[] = c.getClass().getDeclaredFields();





    }

    private ArrayList<Object> fillParams(Parameter formal_params[], Object c) throws MultipleInjectConstructorsException, NoConstructorFoundException, NoSuitableProviderFoundException, MultipleProvidersException, MultipleAnnotationOnParameterException{
        ArrayList<Object> actual_params = new ArrayList<>();
        Annotation annotations[];
        List<Method> params_methods;

        for(Parameter p : formal_params) {
            if(p.getAnnotation(Named.class) != null && this.stringToClassBindings.get(p.getAnnotation(Named.class).name) != null){//1 TODO: maybe check if more than one named or annotation or something
                actual_params.add(construct(this.stringToClassBindings.get(p.getAnnotation(Named.class).name)));
            }  else{//2
                if((p.getAnnotations().length > 0) &&  p.getAnnotation(Named.class) == null){
                    //TODO: check if need to check if there are more than one annotation
                    annotations = p.getAnnotations();
                    final Annotation an = annotations[0].annotationType() == Named.class ? annotations[1] : annotations[0];
                    params_methods = Arrays.stream(an.annotationType().getDeclaredMethods()).filter(m -> (m.getAnnotation(Provides.class) != null) && (m.getAnnotation(an.getClass()) != null) && (m.getReturnType() != p.getClass())).collect(Collectors.toList());
                    if(params_methods.size() > 1){
                        throw new MultipleProvidersException();
                    }
                    if(params_methods.size() == 0){
                        throw new NoSuitableProviderFoundException();
                    }
                    actual_params.add(params_methods.get(0).invoke(c));
                } else{//3
                    actual_params.add(construct(p.getClass()));
                }
            }
        }

        return actual_params;
    }

}