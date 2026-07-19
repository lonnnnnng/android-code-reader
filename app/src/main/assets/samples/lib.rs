pub struct User {
    pub name: String,
}

pub fn greeting(user: &User) -> String {
    format!("Hello, {}", user.name)
}
