import grails.plugins.springsecurity.SpringSecurityService
import org.pillarone.riskanalytics.core.user.UserManagement
import org.pillarone.riskanalytics.core.parameter.comment.Tag
import org.springframework.transaction.TransactionStatus

class BootStrap {


    SpringSecurityService authenticateService = UserManagement.getSpringSecurityService()

    def init = { servletContext ->

        Tag.withTransaction { TransactionStatus status ->
            if (Tag.count() < 4) {
                Tag todo = new Tag(name: "todo")
                save todo
                Tag fixed = new Tag(name: "fixed")
                save fixed
                Tag warning = new Tag(name: "warning")
                save warning
                Tag error = new Tag(name: "error")
                save error
                Tag review = new Tag(name: "review")
                save review
            }
        }

    }

     def destroy = {
     }

    private def save(domain) {
        if (!domain.save()) {
            domain.errors.each { println it }
            throw new RuntimeException("Cannot save ${domain.class} in BootStrap")
        }
    }
}