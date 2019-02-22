export default {
template: `
<div>
  <div>
   <select v-model="application">
     <option v-for="app in applications" v-bind:value="app">
       {{ app.name }}
     </option>
   </select>
   <button v-on:click="loadApplication">Load</button>
 </div>
  <div>
   <select v-model="refType" v-on:change="loadRefValue">
     <option v-for="type in application.referenceType" v-bind:value="type">
       {{ type }}
     </option>
   </select>
   <select v-model="refValue" multiple>
     <option v-for="value in refValues" v-bind:value="value">
       {{ value.refValues }}
     </option>
   </select>
   <button v-on:click="addRefValue">Add</button>
 </div>
 <div>
   <select v-model="selectedRef" multiple>
      <option v-for="value in selectedRefs" v-bind:value="value">
        {{ value.refValues }}
      </option>
    </select>
 </div>
  <div>
   <select v-model="dataType">
     <option v-for="type in application.dataType" v-bind:value="type">
       {{ type }}
     </option>
   </select>
   <input type="text" v-model="outColumn"></input>
   <a v-bind:href="exportUrl">Download</a>
   <button v-on:click="exportCSV">Export</button>
  </div>
</div>
`,
  data() {
    return {
      application: '',
      applications: [],
      refType: '',
      refValue: [],
      refValues: [],
      selectedRef: [],
      selectedRefs: [],
      dataType: '',
      outColumn: "date;espece;plateforme;Nombre d'individus",
      exportUrl: ''
    }
  },
  methods: {
    loadApplication() {
        fetch("/api/v1/applications",
          {method: "GET",
           headers: {
             'Accept': 'application/json'
           }})
          .then(response => {
            if(response.ok) {
              console.log("app loading ok");
              return response.json();
            } else {
              throw new Error("Can't load application." + response.status);
            }
          })
          .then(json => {this.applications = json})
          .catch(error => console.error("login ko", error));
    },
    loadRefValue() {
        fetch(`/api/v1/applications/${this.application.id}/references/${this.refType}`,
          {method: "GET",
           headers: {
             'Accept': 'application/json'
           }})
          .then(response => {
            if(response.ok) {
              console.log("app loading ok");
              return response.json();
            } else {
              throw new Error("Can't load application." + response.status);
            }
          })
          .then(json => {this.refValues = json})
          .catch(error => console.error("login ko", error));
    },
    addRefValue() {
      this.selectedRefs = this.selectedRefs.concat(this.refValue);
      let refs = this.selectedRefs.map(r => r.referenceType + '=' + r.id).join("&")
      this.exportUrl = `/api/v1/applications/${this.application.id}/data/${this.dataType}/csv?${refs}&outColumn=${this.outColumn}`;
    },
    exportCSV() {
        let refs = this.selectedRef.map(r => r.referenceType + '=' + r.id).join("&")
        fetch(`/api/v1/applications/${this.application.id}/data/${this.dataType}?{refs}&outColumn={outColumn}`,
          {method: "GET",
           headers: {
             'Accept': 'text/plain'
           }})
          .then(response => {
            if(response.ok) {
              console.log("app loading ok");
              return response.text;
            } else {
              throw new Error("Can't load application." + response.status);
            }
          })
          .then(json => {this.refValues = json})
          .catch(error => console.error("login ko", error));

    }
  }
}
