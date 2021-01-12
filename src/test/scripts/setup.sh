minikube start
kubectl create ns secrets-test
kubectl -n secrets-test create secret generic apikey --from-literal "value=dummykey"
kubectl -n secrets-test create secret generic username --from-literal "value=dummyuser"
kubectl -n secrets-test create secret generic defaults --from-literal "timeout=10"