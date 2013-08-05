package httpserver;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A MethodWrapper is a wrapper for the reflect.Method class. It allows us to
 * easily invoke a method based on a path without worrying about parsing out
 * the variables and whatnot.
 */
class MethodWrapper {

  private static final String LANG_PATH = "java.lang.";

  private String path;
  private Method method;


  /**
   * Create a MethodWrapper.
   *
   * @param path          The path that will cause the method to be called
   * @param methodName    The name of the method
   * @param callingClass  The class that will call this method
   *
   * @throws HTTPException
   */
  public MethodWrapper(String path, String methodName, Class callingClass)
      throws HTTPException {
    try {
      // Get a list of the parameter types
      List<Class> parameterTypes = new ArrayList<Class>();
      String[] paths = path.split("/");
      StringBuilder pathBuilder = new StringBuilder();

      // Rebuild the path in a universal way
      // TODO: explain that comment
      for (String part : paths) {
        if (!isDynamic(part)) {
          part.toLowerCase();
        }
        else {
          String paramClass = LANG_PATH + part.substring(1, part.length() - 1);
          parameterTypes.add(Class.forName(paramClass));
        }

        // The first path is always empty causing it to have 1 extra / in
        // the front
        if (!part.isEmpty()) {
          pathBuilder.append('/');
          pathBuilder.append(part);
        }
      }

      this.path = pathBuilder.toString();

      // If the path was just a '/' it will be empty
      if (this.path.isEmpty()) {
        this.path = "/";
      }

      // Because Class.getMethod() takes in an array of Classes, and because
      // List.toArray() returns an array of Objects, we need to manually
      // convert parameterTypes from a list to an array.
      Class[] paramTypes = new Class[parameterTypes.size()];
      for (int i=0; i < parameterTypes.size(); i++) {
        paramTypes[i] = parameterTypes.get(i);
      }

      method = callingClass.getMethod(methodName, paramTypes);
    }
    catch(ClassNotFoundException | NoSuchMethodException
        | SecurityException e) {
      throw new HTTPException("Could not add path.", e);
    }
  }


  /**
   * Invoke the method.
   *
   * @param callingClass  The class the method belongs to.
   * @param path          The path that caused the method to be called. This is
   *                      where variables come from.
   *
   * @throws HTTPException
   */
  public void invoke(Object callingClass, String path) throws HTTPException {
    try {
      // Get the parameters
      String[] paths = path.split("/");
      String[] methodPaths = this.path.split("/");
      List<Object> params = new ArrayList<Object>();

      for (int i = 0; i < paths.length; i++) {
        if (isDynamic(methodPaths[i])) {
          Class paramClass = Class.forName(LANG_PATH + methodPaths[i]
              .substring(1, methodPaths[i].length() - 1));

          Constructor paramConstructor
          = paramClass.getConstructor(String.class);

          params.add(paramConstructor.newInstance(paths[i]));
        }
      }


      // Method.invoke throws an exception if an empty array is passed in
      if (params.isEmpty()) {
        method.invoke(callingClass);
      }
      else {
        method.invoke(callingClass, params.toArray());
      }
    }
    catch (IllegalAccessException | IllegalArgumentException
        | InvocationTargetException | SecurityException
        | ClassNotFoundException | NoSuchMethodException
        | InstantiationException e) {
      throw new HTTPException("Could not invoke method.", e);
    }
  }


  /**
   * Determines how correct a method is from a path. The higher the number,
   * the more likely the method is the correct method.
   *
   * Correctness is based on the similarity of the passed in path, and the
   * method's path.
   *
   * @param path  The path, relative the the handler.
   *
   * @return  An integer from 0 to the length of path split on the '/'
   */
  public int howCorrect(String path) {
    String[] paths = path.split("/");
    String[] methodPaths = this.path.split("/");

    // If the paths aren't the same length, this is the wrong method
    if(paths.length != methodPaths.length)
      return 0;

    // Start at one because the paths are the same length
    int count = 1;
    for(int i=0; i < paths.length && i < methodPaths.length; i++) {
      if(paths[i].equals(methodPaths[i]))
        count += 3;

      // Variable checking
      else if(isDynamic(methodPaths[i])){
        try {
          Class<?> paramClass = Class.forName(LANG_PATH + methodPaths[i].substring(1, methodPaths[i].length() - 1));
          Constructor<?> constructor = paramClass.getConstructor(String.class);
          constructor.newInstance(paths[i]);
          count++;
          if(!hasDecimal(paramClass))
            count++;
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
          return 0;
        }

      }
    }

    return count;
  }

  /**
   * Checks if a class allows a decimal or not
   * @param paramClass
   * @return
   */
  private boolean hasDecimal(Class<?> paramClass) {
    return paramClass.equals(BigDecimal.class) ||
        paramClass.equals(Double.class) ||
        paramClass.equals(Float.class);
  }


  /**
   * Checks if there is dynamic text in part of a path.
   *
   * @param path  Part of the path you want to check for dynamic data.
   *
   * @return  If the path matches the regex pattern `\{[A-Za-z0-9]{1,}\}`
   */
  private boolean isDynamic(String path) {
    return path.matches("\\{[A-Za-z0-9]{1,}\\}");
  }

  /**
   * Gets the name of the Method
   *
   * @return the Method's name
   */
  public String getName() {
    return method.getName();
  }

  @Override
  public String toString() {
    return method.toString();
  }
}