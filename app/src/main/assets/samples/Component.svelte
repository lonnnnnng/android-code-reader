<script lang="ts">
  let count: number = 0;
</script>

<button on:click={() => count += 1}>Count: {count}</button>

<style>
  button { color: #0b6bcb; }
</style>
