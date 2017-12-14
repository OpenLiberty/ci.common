package net.wasdev.wlp.common.arquillian.objects;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import net.wasdev.wlp.common.arquillian.util.ArquillianConfigurationException;

public class LibertyProperty {

    public interface LibertyPropertyI {
    }

    /**
     * Converts Map<String, String> to Map<LibertyPropertyI, String>. Validates
     * each key property name.
     * 
     * @param arquillianProperties
     * @return a map of converted arquillianProperties
     * @throws ArquillianConfigurationException
     *             if a property name is invalid.
     */
    public static Map<LibertyPropertyI, String> getArquillianProperties(Map<String, String> arquillianProperties,
            Class<?> cls) throws ArquillianConfigurationException {
        Map<LibertyPropertyI, String> props = new HashMap<LibertyPropertyI, String>();
        if (arquillianProperties != null && !arquillianProperties.isEmpty()) {
            for (Entry<String, String> entry : arquillianProperties.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key != null && value != null) {
                    LibertyPropertyI p = getArquillianProperty(key, cls);
                    props.put(p, value);
                }
            }
        }
        return props;
    }

    /**
     * Check that the given key exists in ArquillianProperties
     * 
     * @param key
     * @return true if so, fail the build otherwise
     * @throws MojoFailureException
     */
    private static LibertyPropertyI getArquillianProperty(String key, Class<?> cls)
            throws ArquillianConfigurationException {
        try {
            if (cls == LibertyManagedObject.LibertyManagedProperty.class) {
                return LibertyManagedObject.LibertyManagedProperty.valueOf(key);
            } else if (cls == LibertyRemoteObject.LibertyRemoteProperty.class) {
                return LibertyRemoteObject.LibertyRemoteProperty.valueOf(key);
            }
        } catch (IllegalArgumentException e) {
            throw new ArquillianConfigurationException(
                    "Property \"" + key + "\" in arquillianProperties does not exist. You probably have a typo.");
        }
        throw new ArquillianConfigurationException("This should never happen.");
    }

}
