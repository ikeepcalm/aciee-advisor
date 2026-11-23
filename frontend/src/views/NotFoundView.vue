<script setup lang="ts">
import { BookOpen, CalendarDays, GraduationCap, Home } from 'lucide-vue-next'
import DeadEndPage from '@/components/DeadEndPage.vue'
</script>

<template>
  <DeadEndPage code="404" :title="$t('notFound.title')" :body="$t('notFound.body')">
    <RouterLink :to="{ name: 'home' }" class="btn btn-primary col-span-2 min-h-12 text-base">
      <Home class="size-4" aria-hidden="true" />
      {{ $t('nav.home') }}
    </RouterLink>
    <RouterLink :to="{ name: 'timetable' }" class="btn btn-outline min-h-12">
      <CalendarDays class="size-4" aria-hidden="true" />
      {{ $t('nav.timetable') }}
    </RouterLink>
    <RouterLink :to="{ name: 'professors' }" class="btn btn-outline min-h-12">
      <GraduationCap class="size-4" aria-hidden="true" />
      {{ $t('nav.professors') }}
    </RouterLink>
    <RouterLink :to="{ name: 'guide' }" class="btn btn-outline col-span-2 min-h-12">
      <BookOpen class="size-4" aria-hidden="true" />
      {{ $t('notFound.guide') }}
    </RouterLink>
  </DeadEndPage>
</template>
