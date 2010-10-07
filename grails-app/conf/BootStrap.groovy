import org.pillarone.riskanalytics.core.parameter.comment.Tag
import org.springframework.transaction.TransactionStatus

class BootStrap {

    def init = {servletContext ->
        Tag.withTransaction { TransactionStatus status ->
            if (Tag.count() < 4) {
                new Tag(name: "todo").save()
                new Tag(name: "fixed").save()
                new Tag(name: "warning").save()
                new Tag(name: "error").save()
                new Tag(name: "review").save()
            }
        }
    }

    def destroy = {
    }
} 