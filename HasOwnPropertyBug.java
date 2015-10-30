import javax.script.*;
import java.util.*;
import jdk.nashorn.api.scripting.*;

public class HasOwnPropertyBug {
  public static void main(String[] args) throws Exception {
    System.out.println(System.getProperty("java.version"));
    
    ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
    Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
    
    // The collection that will be turned into an array
    Collection<String> aCollection = new ArrayList<String>();
    aCollection.add("test");
    
    // The "native object" factory - necessary since we cannot call Java.from from host code.
    Object factoryObject = engine.eval(
      "var factory = {" +
      "  toArray: function(coll) { return Java.from(coll); }" +
      "};" + 
      "factory");
    NativeFactory factory = ((Invocable) engine).getInterface(factoryObject, NativeFactory.class);

    // Create a "receiver" - an object that will receive a function from JavaScript and immediately
    // invokes it with the wrapped collection as argument.
    Receiver receiver = new Receiver() {
      public void provideFunction(AFunction theFunction) {
        theFunction.call(new CollectionWrapper(aCollection, factory));
      }
    };
    bindings.put("receiver", receiver);
    
    // Note: The for loop is is essentially what Jasmine does when testing for equality.
    engine.eval(
      "receiver.provideFunction(function (wrapper) {" +
      "  var array = wrapper.toArray();" +
      "  print('Testing the array...');" +
      "  for (var key in array) {" +
      "    print('Key: ' + key);" +
      "    print('Has: ' + Object.prototype.hasOwnProperty.call(array, key));" +
      "  }" +
      "});");
  }
  
  public static interface NativeFactory {
    Object toArray(Collection<?> aCollection);
  }
  
  public static interface Receiver {
    void provideFunction(AFunction theFunction);
  }
  
  public static interface AFunction {
    void call(Object arg);
  }
  
  public static class CollectionWrapper extends AbstractJSObject {
    private Collection<?> aCollection;
    private NativeFactory nativeFactory;
    public CollectionWrapper(Collection<?> aCollection, NativeFactory nativeFactory) {
      this.aCollection = aCollection;
      this.nativeFactory = nativeFactory;
    }
    public Object getMember(String name) {
      if ("toArray".equals(name)) {
        Object nativeArray = nativeFactory.toArray(aCollection);
        System.out.println("Native array class: " + nativeArray.getClass().getName());
        return valueFn(nativeArray);
      }
      return null;
    }
    
    private JSObject valueFn(Object value) {
      return new AbstractJSObject() {
        public Object call(Object thiz, Object... args) {
          return value;
        }
      };
    }
  }
}