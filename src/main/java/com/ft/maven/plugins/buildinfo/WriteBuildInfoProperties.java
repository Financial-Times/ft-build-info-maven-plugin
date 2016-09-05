package com.ft.maven.plugins.buildinfo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.apache.maven.execution.RuntimeInformation;
import org.apache.maven.model.Profile;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.codehaus.plexus.util.StringUtils;

/**
 * Writes maven build information to a properties file.
 * 
 * By default will write to the file build-info.properties in the target/classes directory.
 * 
 * @version 1.0
 * @goal write-build-info-properties
 */
public class WriteBuildInfoProperties extends AbstractMojo implements Contextualizable {

    private static final String DEFAULT_PROPERTY_PREFIX = "build.";
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    
	private static final String EMPTY_STRING = "";
    
    private static final String[] DEFAULT_SYSTEM_PROPERTIES = {
    	"os.arch", "os.name", "os.version", "java.vm.name", "java.vm.vendor", "java.version"
    };

	private final List<String> defaultSystemProperties = Collections.unmodifiableList(Arrays.asList(DEFAULT_SYSTEM_PROPERTIES));

	/**
	 * @parameter default-value="${project}"
	 * @required
	 */
	protected MavenProject project;

	/**
	 * The fileName that will be written to under target/classes.
	 * 
	 * @parameter default-value="build-info.properties"
	 * @required
	 */
	protected String fileName;

	/**
	 * The prefix for properties to add to the build information
	 * 
	 * @parameter
	 */
	protected String prefix = DEFAULT_PROPERTY_PREFIX;
	
	
    protected PlexusContainer container;

    public void contextualize(Context context) throws ContextException {
        container = (PlexusContainer)context.get(PlexusConstants.PLEXUS_KEY);
    }
	
	public void execute() throws MojoExecutionException, MojoFailureException {
		Map<String, String> buildProperties = prepareBuildProperties();
		writeProperties(buildProperties, fileName);
	}

	private Map<String, String> prepareBuildProperties() {
		Map<String, String> buildProperties = new TreeMap<String, String>();

		addMavenProperties(buildProperties);
		addArtifactProperties(buildProperties);
		addDefaultSystemProperties(buildProperties);
		addPrefixedProjectProperties(buildProperties, prefix);
		addPrefixedSystemProperties(buildProperties, prefix);
		
		return buildProperties;
	}

	private void addMavenProperties(Map<String, String> buildProperties) {
		buildProperties.put(prefix + "maven.version", getMavenVersion());
		buildProperties.put(prefix + "maven.activeProfiles", getMavenActiveProfiles());
	}

	private String getMavenVersion() {
		try {
			RuntimeInformation rti = (RuntimeInformation) container.lookup(RuntimeInformation.class.getName());
			return rti.getApplicationVersion().toString();
		} catch (ComponentLookupException e) {
			getLog().warn("Unable to lookup Maven RuntimeInformation: " + e.getLocalizedMessage(), e);
			return EMPTY_STRING;
		}
	}

	private String getMavenActiveProfiles() {
		List<Profile> activeProfiles = project.getActiveProfiles();
		List<String> activeProfileIds = new ArrayList<String>();
		for (Profile profile : activeProfiles) {
			activeProfileIds.add(profile.getId());
		}
		return StringUtils.join(activeProfileIds.iterator(), ", ");
	}

	private void addArtifactProperties(Map<String, String> buildProperties) {
		buildProperties.put("artifact.id", project.getArtifactId());
		buildProperties.put("artifact.groupId", project.getGroupId());
		buildProperties.put("artifact.version", project.getVersion());
	}

    private void addDefaultSystemProperties(Map<String, String> buildProperties) {
        List<String> properties = defaultSystemProperties;
        if (properties != null) {
            for (String property : properties) {
                buildProperties.put(prefix + property, System.getProperty(property, EMPTY_STRING));
            }
        }
    }

	private void addPrefixedProjectProperties(Map<String, String> buildProperties, String prefix) {
		getPrefixedProperties(buildProperties, prefix, project.getProperties());
	}

    private void addPrefixedSystemProperties(Map<String, String> buildProperties, String prefix) {
    	getPrefixedProperties(buildProperties, prefix, System.getProperties());
    }

	private void getPrefixedProperties(Map<String, String> buildProperties, String prefix, Properties properties) {
		for (String propertyName : properties.stringPropertyNames()) {
        	if (propertyName.startsWith(prefix)) {
                buildProperties.put(propertyName, properties.getProperty(propertyName, EMPTY_STRING));
        	}
        }
	}

    protected void writeProperties(Map<String, String> buildProperties, String filename) throws MojoExecutionException {
		
        String fileName = prepareFile(filename);

        getLog().info("Writing to the file " + fileName);

        Writer out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), "UTF-8"));
            for (Map.Entry<String, String> entry : buildProperties.entrySet()) {
            	String line = String.format("%s=%s", entry.getKey(), entry.getValue());
                out.write(line);
                out.write(LINE_SEPARATOR);
                getLog().info(line);
            }
            out.flush();
        } catch (IOException e) {
            getLog().warn(e.getMessage());
        } finally {
        	try {
        		if (out != null) {
        			out.close();
        		}
            } catch (IOException e) {
            	getLog().warn(e.getMessage());
            }
        }
		
	}

	private String prepareFile(String filename) throws MojoExecutionException {
		String fileName = project.getBuild().getOutputDirectory() + File.separator + filename;

		File file = new File(fileName);
		
		if (file.isDirectory()) {
			throw new MojoExecutionException("fileName must be a file and not a directory");
		}
		
		// ensure path exists
		if (file.getParentFile() != null) {
			file.getParentFile().mkdirs();
		}
		
		return fileName;
	}

}
