<template>
  <div>
    <v-layout
      v-if="dataType!=null"
      wrap
      align-center
    >
      <v-flex
        md7
        offset-md2
      >
        <v-select
          @input="setDataset"
          name="datasets"
          :items="dataType"
          label="Choisissez un type de données"
        />
      </v-flex>
      <v-flex
        md1
        offset-md1
        fluid
        v-if="datasetName"
      >
        <v-tooltip bottom>
          <template v-slot:activator="{ on }">
            <v-btn
              @click="pickFile"
              color="primary"
              flat
              v-on="on"
            >
              {{ datasetName }}
              <v-icon
                right
                x-large
                color="success"
              >
                mdi-upload
              </v-icon>
            </v-btn>
          </template>
          <span>Charger des données pour {{ datasetName }}</span>
        </v-tooltip>
        <input
          style="display:none"
          type="file"
          id="referencefileUpload"
          ref="file"
          @change="handleFileUpload()"
        >
      </v-flex>
    </v-layout>
    <v-layout
      v-if="datasetName!=null"
      wrap
      align-center
    >
      <v-flex
        xs12
        sm6
        d-flex
      >
        <v-data-table
          v-model="selected"
          :custom-sort="customSort"
          :headers="headers"
          :items="datasetValue"
          select-all
          item-key="name"
          class="elevation-1"
        >
          <template v-slot:headers="props">
            <tr>
              <th>
                <v-checkbox
                  :input-value="props.all"
                  :indeterminate="props.indeterminate"
                  primary
                  hide-details
                  @click.stop="toggleAll"
                />
              </th>
              <th
                v-for="header in props.headers"
                :key="header.text"
                @click="changeSort(header.value)"
              >
                <!--v-icon small>arrow_upward</v-icon-->
                {{ header.text }}
              </th>
            </tr>
          </template>
          <template v-slot:items="props">
            <tr
              :active="props.selected"
              @click="props.selected = !props.selected"
            >
              <td>
                <v-checkbox
                  :input-value="props.selected"
                  primary
                  hide-details
                />
              </td>
              <td
                v-for="key in headers"
                :key="key.value"
              >
                {{ props.item[key.value] }}
              </td>
            </tr>
          </template>
        </v-data-table>
      </v-flex>
    </v-layout>
    <v-snackbar
      top
      vertical
      v-model="snackbar.visible"
    >
      {{ snackbar.text }}
      <v-btn
        flat
        @click="snackbar.visible=false"
        class="red--text"
      >
        fermer
        <v-icon color="red">
          mdi-close
        </v-icon>
      </v-btn>
    </v-snackbar>
  </div>
</template>

<script>
import { storage, Storage } from "@/storage";
import store from "@/store";
import EventBus from "@/eventBus";
export default {
  name: "Datasets",
  mounted() {
    EventBus.$on("dataset:uploaded", event => {
      if (event.id) {
        this.snackbar.text = "le jeu de données a été chargé";
        this.snackbar.visible = true;
        if (this.datasetName != null) {
          this.setDataset(this.datasetName);
        }
      } else {
        let message = "le jeu de données n'a pas pu être chargé.";
        if (event.status == 400) {
          event.text().then(text => {
            message += text;
            this.snackbar.text = message;
            this.snackbar.visible = true;
          });
        } else if (event.status == 401) {
          message +=
            " Vous n'êtes pas autorisé à charger cette donnée";
        } else if (event.status == 403) {
          message +=
            " Vous n'avez pas les droits pour charger cette donnée";
        } else if (event.status == 404) {
          message = "Le service est indisponible pour le moment.";
        } else if (event.status == 500) {
          message = "Le service est indisponible pour le moment.";
        }
        this.snackbar.text = message;
        this.snackbar.visible = true;
      }
    });
  },
  computed: {
    datasets :{
      get(){
        return store.state.configuration != null
          ? store.state.configuration.dataset
          : null
      }
    },
    dataType :{
      get(){
        return store.state.dataType
      }
    },
    datasetDescription: {
      get() {
        return store.state.datasetDescription;
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
        const datasetDescription = store.state.datasetDescription;
        if(datasetDescription==null){
          return []
        }
        return Object.keys(datasetDescription.data)
        .map(a=>{return {text:a, align:'center', value:a}});
      }
    }
  },
  props: {
  },
  data() {
    return {
      snackbar: {
        visible: false,
        text: ""
      },
      pagination: {
        sortBy: "value"
      },
      selected: [],
      //sheet: false,
      file: null
    };
  },
  methods: {
    setDataset(datasetName) {
      store.dispatch("loadDataset", {
        datasetName:datasetName, 
        datasetDescription:this.datasets[datasetName]
      });
    },
    toggleAll() {
      if (this.selected.length) this.selected = [];
      else this.selected = this.datasetValue;
    },
    changeSort(column) {
      if (this.pagination.sortBy === column) {
        this.pagination.descending = !this.pagination.descending;
      } else {
        this.pagination.sortBy = column;
        this.pagination.descending = !this.pagination.descending;
      }
    },
    customSort(items, index, isDescending) {
      if (index === null) return items;
      return items.sort(function(a, b) {
        var sortA = a[index];
        var sortB = b[index];
        if (isDescending) {
          var _ref = [sortB, sortA];
          sortA = _ref[0];
          sortB = _ref[1];
        }

        if (sortA > sortB) return 1;
        if (sortA < sortB) return -1;
        return 0;
      });
    },
    handleFileUpload(event) {
      /** */
      this.file = this.$refs.file.files[0];
      if (this.file != null) {
        store.dispatch("uploadDataset", {
          datasetName: this.datasetName,
          file: this.file
        });
        this.resetValidation();
      }
    },
    pickFile() {
      this.$refs.file.value = null;
      this.$refs.file.click();
    },
  },
  components: {
  }
};
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
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
