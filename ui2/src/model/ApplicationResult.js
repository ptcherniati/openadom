export class ApplicationResult {
  id;
  name;
  title;
  references = {
    idRef: {
      id: "",
      label: "",
      children: [],
      columns: {
        id: "",
        title: "",
        key: false,
        linkedTo: "",
      },
    },
  };
}
