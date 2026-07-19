namespace Sample;

public sealed record User(long Id, string Name);

public static class Program
{
    public static void Main() => Console.WriteLine(new User(1, "C#"));
}
