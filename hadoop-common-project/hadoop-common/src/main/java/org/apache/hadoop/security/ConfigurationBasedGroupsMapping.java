package org.apache.hadoop.security;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import org.apache.commons.io.Charsets;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.StringUtils;

import java.io.IOException;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.apache.hadoop.fs.CommonConfigurationKeysPublic.HADOOP_SECURITY_CONFIGURATIONBASED_GROUP_MAPPING_FILE;

@InterfaceAudience.LimitedPrivate({"HDFS", "MapReduce"})
@InterfaceStability.Evolving
public class ConfigurationBasedGroupsMapping
        implements GroupMappingServiceProvider {

  private static final Log LOG = LogFactory.getLog(ConfigurationBasedGroupsMapping.class);

  private HashMultimap<String, String> user2groups = HashMultimap.create();

  public ConfigurationBasedGroupsMapping() {
    this(new Configuration());
  }

  public ConfigurationBasedGroupsMapping(Configuration conf) {
    try {
      this.reload(conf);
    } catch (IOException e) {
      throw new IllegalStateException(StringUtils.stringifyException(e));
    }
  }

  @VisibleForTesting
  protected HashMultimap<String, String> getUser2groups() {
    return this.user2groups;
  }

  @Override
  public Set<String> getGroups(String user) throws IOException {
    return this.user2groups.get(user);
  }

  @Override
  public void cacheGroupsRefresh() throws IOException {

    Configuration conf = new Configuration();
    this.reload(conf);

  }

  /**
   * Adds groups to cache, no need to do that for this provider
   *
   * @param groups unused
   */
  @Override
  public void cacheGroupsAdd(List<String> groups) throws IOException {
    // does nothing in this provider of user to groups mapping
  }

  /**
   * load configuration file of group mapping.
   * @param conf
   * @throws IOException
   */
  protected void reload(Configuration conf) throws IOException {
    // load fixed white list
    String filename = conf.get(HADOOP_SECURITY_CONFIGURATIONBASED_GROUP_MAPPING_FILE);
    if (filename == null || filename.isEmpty()) {
      LOG.error(HADOOP_SECURITY_CONFIGURATIONBASED_GROUP_MAPPING_FILE + " not configured.");
      return;
    }

    File file = new File(filename);
    if (!file.exists()) {
      LOG.error(filename + " not exists!");
      return;
    }

    // new set
    HashMultimap<String, String> newUser2groups = HashMultimap.create();
    LOG.info("Loading new group mapping file: " + filename);
    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), Charsets.UTF_8))) {

      String line;
      // group1=user1,user2,user3
      while ((line = reader.readLine()) != null) {

        if (LOG.isDebugEnabled()) {
          LOG.debug("Loading new group mapping file: Handle " + line);
        }

        Collection<String> groupToUsers = StringUtils.getStringCollection(line,
                "=");
        if (groupToUsers.size() != 2) {
          LOG.warn("ignore invalid mapping: " + line);
          continue;
        }

        String[] groupToUsersArray = groupToUsers.toArray(new String[groupToUsers
                .size()]);
        String group = groupToUsersArray[0];
        for (String user : StringUtils.getStringCollection(groupToUsersArray[1])) {
          newUser2groups.put(user, group);
        }
      }
    }

    LOG.info("Loaded " + newUser2groups.keySet().size() + " users from new group mapping file: " + filename);

    // switch reference
    this.user2groups = newUser2groups;
  }
}
