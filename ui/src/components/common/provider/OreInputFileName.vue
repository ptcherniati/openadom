<template>

    <ValidationProvider rules="required" v-slot="{ errors }">
        <input v-model="val" type="text">
        <span id="error">{{ errors[0] }}</span>
    </ValidationProvider>
</template>

<script>
import {extend, ValidationProvider} from "vee-validate";
import {LOCAL_STORAGE_LANG} from "@/services/Fetcher";
import {ref, watch} from "vue";

const defaultLanguage = localStorage.getItem(LOCAL_STORAGE_LANG);
export default {
    setup(props) {
        const val = ref("");
        watch(
            () => props.value,
            () => {
                val.value = ref(props.value);
            }
        );
        return {val};
    },
    name: "OreInputFileName",
    emits: ["update:value"],
    components: {
        ValidationProvider,
    },
    props: {
        fileName: {
            type: String,
            required: true,
        },
        fileNames: {
            type: Array,
            required: true,
        },
        vid: {
            type: String,
            required: false,
        },
    },
    data: () => {
        return {
            defaultLanguage,
        };
    },
    methods: {
        extend,
        updateValue(event) {
            if (typeof event == "object") {
                event = event.target.value;
            }
            this.$emit("update:value", event);
        },
    },
    computed: {
       /* rules: {
            get() {
                let rules = [];
                return rules.join("|");
            },
        },*/
    },
};
</script>

<style scoped></style>