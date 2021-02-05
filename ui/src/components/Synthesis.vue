<template>
  <v-container
    v-if="dataType!=null"
    class="dataset"
    fluid
    grid-list-xl
  >
    <v-layout
      v-if="dataType!=null"
      wrap
      align-center
    >
      <v-flex
        xs12
        sm6
        d-flex
      >
        <v-select
          @input="setDataset"
          name="datasets"
          :items="dataType"
          label="Choisissez un type de données"
        />
        <v-btn
          flat
          class="body-2"
        >
          {{ localStore.state.datasetName }}
        </v-btn>
      </v-flex>
    </v-layout>
    <v-layout
      v-if="localStore.state.datasetName!=null && datasets[localStore.state.datasetName]!=null"
      wrap
      align-center
    >
      <v-flex
        xs12
        sm6
        d-flex
      >
        <v-select
          @input="setVariable"
          name="variables"
          :items="variables"
          label="Choisissez une variable"
        />
        <v-btn
          flat
          class="body-2"
        >
          {{ localStore.state.variableName }}
        </v-btn>
      </v-flex>
    </v-layout>
    <GChart
      type="LineChart"
      :data="chartData"
      :options="chartOptions"
    />
  </v-container>
</template>

<script>
import store from "@/store";
import Vuex from "vuex";
import { GChart } from "vue-google-charts";
export default {
  name: "Synthesis",
  computed: {
    datasets: {
      get() {
        return store.state.configuration != null
          ? store.state.configuration.dataTypes
          : null;
      }
    },
    dataType: {
      get() {
        return store.state.dataType;
      }
    },
    datasetDescription: {
      get() {
        return store.state.datasetDescription;
      }
    },
    variables: {
      get() {
        return this.localStore.state.datasetName != null
          ? Object.keys(this.datasets[this.localStore.state.datasetName].data)
          : null;
      }
    },
    datasetName: {
      get() {
        return store.state.datasetName;
      }
    },
    datasetValue: {
      get() {
        return store.state.datasetValue;
      }
    },
    headers: {
      get() {
        if (store.state.datasetDescription == null) {
          return [];
        }
        return Object.keys(store.state.datasetDescription.references)
          .concat(Object.keys(store.state.datasetDescription.data))
          .map(a => {
            return { text: a, align: "center", value: a };
          });
      }
    }
  },
  data() {
    return {
      // Array will be automatically processed with visualization.arrayToDataTable function
      chartData: [
        ['Year', 'Sales', 'Expenses', 'Profit'],
        ['2014', 1000, 400, 200],
        ['2015', 1170, 460, 250],
        ['2016', 660, 1120, 300],
        ['2017', 1030, 540, 350]
      ],
      chartOptions: {
        chart: {
          title: 'Company Performance',
          subtitle: 'Sales, Expenses, and Profit: 2014-2017',
        }
      },
      localStore: new Vuex.Store({
        state: {
          datasetName: null,
          variableName: null,
          data: null,
          dataDescription: null
        },
        mutations: {
          setDataset(state, payload) {
            this.state.datasetName = payload.datasetName;
            this.state.dataDescription =
              store.state.configuration.dataTypes[payload.datasetName];
          },
          setVariable(state, payload) {
            this.state.variableName = payload.variableName;
            this.state.data = store.state.data;
          }
        },
        actions: {}
      })
    };
  },
  methods: {
    setDataset(datasetName) {
      this.localStore.commit("setDataset", {
        datasetName: datasetName
      });
    },
    setVariable(variableName) {
      this.localStore.commit("setVariable", {
        variableName: variableName
      });
    },
    loadDataset({ commit }, dataset) {
      http
        .loadDataset(dataset.datasetName, config.APPLICATION_NAME)
        .then(response => {
          response.json().then(function(data) {
            commit("setDataset", {
              datasetValue: data,
              datasetName: dataset.datasetName,
              datasetDescription: dataset.datasetDescription
            });
          });
        })
        .catch(error => console.log(error));
    }
  },
  components: {
    GChart
  }
};
</script>

<style scoped lang="scss">
.references > table > tr > td {
  border: solid 1px red;
}
h3 {
  margin: 40px 0 0;
}
ul {
  list-style-type: none;
  padding: 0;
}
li {
  display: inline-block;
  margin: 0 10px;
}
a {
  color: #42b983;
}
</style>
