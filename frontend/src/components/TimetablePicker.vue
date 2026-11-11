<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { TimetableProgram } from '@/api'

const props = defineProps<{ programs: TimetableProgram[]; group: string | null }>()
const emit = defineEmits<{ choose: [group: string] }>()

const ROMAN = ['', 'I', 'II', 'III', 'IV']
const { t } = useI18n()
const current = props.programs.find((p) => props.group && p.groups.includes(props.group))
const year = ref(current?.year ?? 1)
const code = ref(current?.code ?? null)

const yearPrograms = computed(() => props.programs.filter((p) => p.year === year.value))
const picked = computed(() => props.programs.find((p) => p.code === code.value))

function chooseYear(y: number) {
  year.value = y
  code.value = yearPrograms.value.length === 1 ? yearPrograms.value[0]!.code : null
}

function chooseProgram(p: TimetableProgram) {
  code.value = p.code
  if (p.groups.length === 1) {
    emit('choose', p.groups[0]!)
  }
}
</script>

<template>
  <div class="picker">
    <p class="step">{{ t('timetable.pickYear') }}</p>
    <div class="chips">
      <button
        v-for="y in [1, 2, 3, 4]"
        :key="y"
        class="chip year"
        :class="{ on: year === y }"
        @click="chooseYear(y)"
      >
        {{ ROMAN[y] }}
      </button>
    </div>
    <p class="step">{{ t('timetable.pickProgram') }}</p>
    <div class="programs">
      <button
        v-for="p in yearPrograms"
        :key="p.code"
        class="program"
        :class="{ on: code === p.code }"
        @click="chooseProgram(p)"
      >
        <span>{{ p.name }}</span>
        <span class="codes">{{ p.groups.join(' · ') }}</span>
      </button>
    </div>
    <template v-if="picked && picked.groups.length > 1">
      <p class="step">{{ t('timetable.pickGroup') }}</p>
      <div class="chips">
        <button
          v-for="g in picked.groups"
          :key="g"
          class="chip code"
          :class="{ on: g === group }"
          @click="emit('choose', g)"
        >
          {{ g }}
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.step {
  margin: 1.4rem 0 0.5rem;
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--copper);
}

.step:first-child {
  margin-top: 0;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.chip {
  min-width: 3rem;
  padding: 0.6rem 1rem;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--panel);
  font-weight: 600;
}

.chip.year {
  min-width: 3.5rem;
  font-family: var(--font-display);
  font-size: 1.125rem;
}

.chip.code {
  font-family: var(--font-mono);
  font-weight: 500;
}

.chip.on,
.chip:hover {
  border-color: var(--mark);
  background: var(--mark);
  color: var(--on-mark);
}

.programs {
  display: grid;
  gap: 0.5rem;
}

.program {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.25rem;
  padding: 0.85rem 1rem;
  text-align: left;
  border: 1px solid var(--line);
  border-left: 4px solid var(--line);
  border-radius: var(--wobble);
  background: var(--panel);
  font-weight: 600;
}

.codes {
  font-family: var(--font-mono);
  font-size: 0.6875rem;
  font-weight: 400;
  color: var(--muted);
  overflow-wrap: anywhere;
}

.program.on,
.program:hover {
  border-left-color: var(--copper);
}
</style>
