<template>
  <DialogFrame @cancel="cancel">
    <div class="title">Android エンジン選択</div>
    <div class="form-group">
      <div class="column engine-list">
        <div v-if="loading" class="engine">
          エンジンを検索中...
        </div>
        <div v-else-if="engines.length === 0" class="engine">
          インストールされている OEX 対応エンジンが見つかりません。
        </div>
        <div
          v-for="engine in engines"
          :key="engine.packageName"
          class="row engine selectable"
          @click="select(engine)"
        >
          <div class="column">
            <div class="engine-name">{{ engine.name }}</div>
            <div class="package-name">{{ engine.packageName }}</div>
          </div>
        </div>
      </div>
    </div>
    <div class="main-buttons">
      <button data-hotkey="Escape" @click="cancel()">
        {{ t.cancel }}
      </button>
    </div>
  </DialogFrame>
</template>

<script setup lang="ts">
import { t } from "@/common/i18n";
import { onMounted, ref } from "vue";
import { USIEngine } from "@/renderer/ipc/android";
import DialogFrame from "./DialogFrame.vue";

const emit = defineEmits<{
  select: [engine: { name: string; packageName: string; type: "aidl" | "oex"; authority?: string }];
  cancel: [];
}>();

const engines = ref<
  { name: string; packageName: string; type: "aidl" | "oex"; authority?: string }[]
>([]);
const loading = ref(true);

onMounted(async () => {
  console.log("OEXEngineSelectorDialog mounted!");
  try {
    const { engines: list } = await USIEngine.getEngines();
    console.log("getEngines result:", JSON.stringify(list));
    engines.value = list;
  } catch (e) {
    console.error("getEngines failed:", e);
  } finally {
    loading.value = false;
  }
});

const select = (engine: {
  name: string;
  packageName: string;
  type: "aidl" | "oex";
  authority?: string;
}) => {
  emit("select", engine);
};

const cancel = () => {
  emit("cancel");
};
</script>

<style scoped>
.engine-list {
  width: 100%;
  max-width: 400px;
  max-height: 400px;
  overflow: auto;
}
.engine {
  margin: 0px 5px 0px 5px;
  padding: 10px;
  border-bottom: 1px solid gray;
}
.selectable {
  cursor: pointer;
}
.selectable:hover {
  background-color: var(--pushed-selector-bg-color);
}
.engine-name {
  text-align: left;
  font-weight: bold;
}
.package-name {
  text-align: left;
  font-size: 0.8em;
  color: gray;
}
</style>
