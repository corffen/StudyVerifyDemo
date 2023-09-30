import org.gradle.api.Plugin
import org.gradle.api.Project

class DependenciesPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        println(">>>>>>>>   " + this.getClass().getName())
    }
}