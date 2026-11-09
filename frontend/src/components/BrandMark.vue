<template>
  <!-- An IC package with a clock trace: the week signal of the timetable, soldered to the brand. -->
  <svg viewBox="0 0 32 32" aria-hidden="true" fill="none">
    <rect x="6" y="6" width="20" height="20" rx="3" fill="var(--mask)" />
    <g stroke="var(--mask)" stroke-width="2" stroke-linecap="round">
      <path
        d="M11 6V2M16 6V2M21 6V2M11 30v-4M16 30v-4M21 30v-4M6 11H2M6 16H2M6 21H2M30 11h-4M30 16h-4M30 21h-4"
      />
    </g>
    <path
      d="M9 20h3v-8h4v8h4v-8h3"
      stroke="var(--copper)"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>
</template>
