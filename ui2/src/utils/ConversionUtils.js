export function convertReferencesToTrees(references) {
  const array = references.filter((ref) => {
    return !references.some(
      (r) => r.children && r.children.length !== 0 && r.children.some((c) => c === ref.id)
    );
  });
  return convert(array, references);
}

function convert(references, initialRef) {
  references.forEach((ref) => {
    if (ref.children && ref.children.length !== 0) {
      const children = ref.children.map((c) => initialRef.find((r) => r.id === c));
      ref.children = convert(children, initialRef);
    } else {
      return ref;
      //   const parentIndex = initialRef.findIndex(
      //     (r) => r.children && r.children.length !== 0 && r.children.some((c) => c === ref.id)
      //   );
      //   if (!parentIndex) {
      //     return ref;
      //   } else {
      //     return (initialRef[parentIndex].children = initialRef[parentIndex].children.map((c) => {
      //       if (c === ref.id) {
      //         return ref;
      //       }
      //       return c;
      //     }));
      //   }
    }
  });
  return references;
}
