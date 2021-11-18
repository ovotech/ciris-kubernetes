minikube start
kubectl create ns secrets-test
kubectl -n secrets-test create secret generic apikey --from-literal "value=dummykey"
kubectl -n secrets-test create secret generic username --from-literal "value=dummyuser"
kubectl -n secrets-test create secret generic defaults --from-literal "timeout=10"

kubectl create ns pizza
kubectl -n pizza create configmap pizzabrand --from-literal=brand=domino
kubectl -n pizza create configmap delivery --from-literal=radius=5 --from-literal=charge=true