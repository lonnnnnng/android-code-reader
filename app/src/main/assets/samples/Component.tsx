type Props = { title: string; count: number };

export function Counter({ title, count }: Props) {
  return <section><h2>{title}</h2><output>{count}</output></section>;
}
