export async function loadUser(id) {
  const response = await fetch(`/api/users/${id}`);
  return response.json();
}
