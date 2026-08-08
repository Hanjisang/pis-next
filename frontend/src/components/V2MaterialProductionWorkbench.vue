<script setup lang="ts">
import V2GrossingWorkbench from './V2GrossingWorkbench.vue';
import V2SlideProductionWorkbench from './V2SlideProductionWorkbench.vue';

const props = withDefaults(defineProps<{ sourceType?: string; sourceReferenceId?: string }>(), {
  sourceType: 'INITIAL',
  sourceReferenceId: undefined,
});
const caseId = defineModel<string>('caseId', { default: '' });
</script>

<template>
  <section aria-label="V2-I02 材料生产主链">
    <V2GrossingWorkbench
      v-model:case-id="caseId"
      :source-type="props.sourceType"
      :source-reference-id="props.sourceReferenceId"
    />
    <V2SlideProductionWorkbench v-model:case-id="caseId" />
  </section>
</template>
