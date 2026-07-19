from dataclasses import dataclass


@dataclass
class User:
    name: str


def greet(user: User) -> str:
    return f"Hello, {user.name}"
