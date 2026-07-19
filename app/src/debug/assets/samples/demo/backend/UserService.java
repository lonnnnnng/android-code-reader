package demo.backend;

public final class UserService {
    // 手机端演示目录树时保留真实业务语义，便于同时验证嵌套路径和 Java 高亮。
    public String findUser(long userId) {
        return "user-" + userId;
    }
}
