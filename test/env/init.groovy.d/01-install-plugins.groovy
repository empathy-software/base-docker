
import java.nio.file.Files
import java.nio.file.Paths
import jenkins.model.*
import hudson.model.*
import java.util.logging.Logger

def log = Logger.getLogger("")
def instance = Jenkins.get()
def pm = instance.pluginManager
def uc = instance.updateCenter

println "--> Updating plugin metadata..."
try {
  def siteRefresh = uc.updateAllSites()
  if (siteRefresh instanceof Collection) {
    siteRefresh.each { f ->
      try {
        f.get()
      } catch (Exception e) {
        log.warning "--> Update site refresh failed: ${e.message}"
      }
    }
  }
} catch (Exception e) {
  log.warning "--> updateAllSites failed: ${e.message}"
}

def dataDeadline = System.currentTimeMillis() + 90_000
while (System.currentTimeMillis() < dataDeadline) {
  if (uc.sites.any { it.getData() != null }) {
    break
  }
  sleep(2000)
}

def pluginNames = [
  "workflow-aggregator", "workflow-job", "workflow-cps", "workflow-multibranch",
  "workflow-scm-step", "workflow-durable-task-step", "pipeline-stage-view",
  "pipeline-input-step", "pipeline-github-lib",
  "pipeline-model-api", "pipeline-model-definition", "pipeline-model-extensions",
  "pipeline-utility-steps",

  "git", "git-client", "github", "ssh-credentials", "scm-api",

  "credentials", "credentials-binding", "plain-credentials"
]

def jobs = []
pluginNames.each { pluginName ->
  if (pm.getPlugin(pluginName)) {
    log.info "--> Plugin already installed: ${pluginName}"
    return
  }
  def plugin = uc.getPlugin(pluginName)
  if (plugin) {
    log.info "--> Installing plugin: ${pluginName}"
    jobs << plugin.deploy(true)
  } else {
    log.warning "--> Plugin not found in update center: ${pluginName}"
  }
}

jobs.each { f ->
  try {
    f.get()
  } catch (Exception e) {
    log.severe "--> Plugin deploy failed: ${e.message}"
  }
}

def idleDeadline = System.currentTimeMillis() + 180_000
while (System.currentTimeMillis() < idleDeadline) {
  def pending = uc.jobs.findAll { j ->
    // ConnectionCheckJob has no status; ?. does not catch a missing property.
    if (j.hasProperty("status") == null) {
      return false
    }
    def name = j.status?.getClass()?.getSimpleName()
    name in ["Pending", "Installing"]
  }
  if (pending.isEmpty()) {
    break
  }
  log.info "--> Waiting for ${pending.size()} update-center job(s)..."
  sleep(2000)
}

instance.save()

def markerPath = Paths.get("/var/jenkins_home/.jenkins-ready")
if (!Files.exists(markerPath)) {
    println "--> Marking Jenkins init complete"
    Files.createFile(markerPath)
}
