<template>
  <svg
    viewBox="0 0 32 32"
    aria-hidden="true"
    fill="none"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    class="note-star"
  >
    <path
      d="M5.2 5.6 26.3 4.4 27.6 19.3 20.4 27.6 6 27.2Z"
      fill="var(--mark)"
      stroke="var(--ink)"
    />
    <path
      d="M27.6 19.3 21.3 20.3 20.4 27.6Z"
      fill="color-mix(in srgb, var(--mark) 70%, #20263d)"
      stroke="var(--ink)"
    />
    <path
      class="note-star-doodle"
      pathLength="1"
      d="M15.9 6.9 17.3 12.2 22.7 12.8 18.1 15.8 19.2 21 15 18.2 10.6 20 11.9 15.4 8.6 11.8 13.4 11.6Z"
      stroke="var(--on-mark)"
    />
  </svg>
</template>

<style scoped>
.note-star {
  rotate: -4deg;
}

@media (prefers-reduced-motion: no-preference) {
  .note-star {
    animation: note-star-drop 0.3s ease-out both;
  }

  .note-star-doodle {
    stroke-dasharray: 1;
    animation: note-star-draw 0.6s 0.25s ease-out both;
  }
}

@keyframes note-star-drop {
  from {
    opacity: 0;
    rotate: 3deg;
    translate: 0 -6px;
  }
}

@keyframes note-star-draw {
  from {
    stroke-dashoffset: 1;
  }
}
</style>
