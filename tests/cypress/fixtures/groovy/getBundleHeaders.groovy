import org.osgi.framework.*
import org.jahia.osgi.*

Bundle testBundle = BundleUtils.getBundleBySymbolicName("BUNDLE_SYMBOLIC_NAME", null)

Dictionary<String,String> headers = testBundle.getHeaders()
Enumeration<String> keyIter = headers.keys()
String result = ""
while (keyIter.hasMoreElements()) {
    String key = keyIter.nextElement()
    result += "$key: " + headers.get(key) + " -- "
}
setResult(result)
